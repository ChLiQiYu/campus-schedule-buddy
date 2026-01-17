package com.fjnu.schedule

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fjnu.schedule.data.AppDatabase
import com.fjnu.schedule.data.CourseRepository
import com.fjnu.schedule.jw.JwSchool
import com.fjnu.schedule.jw.JwSchoolCatalog
import com.fjnu.schedule.model.Course
import com.fjnu.schedule.util.ConflictFilterResult
import com.fjnu.schedule.util.CourseImportHelper
import com.fjnu.schedule.util.JwScheduleParser
import com.fjnu.schedule.widget.ScheduleWidgetProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JwImportActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var importFab: FloatingActionButton
    private lateinit var repository: CourseRepository
    private lateinit var school: JwSchool
    private var semesterId: Long = 0L

    private var resolvedLoginUrl: String = ""
    private var resolvedScheduleUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jw_import)

        semesterId = intent.getLongExtra(EXTRA_SEMESTER_ID, 0L)
        if (semesterId <= 0L) {
            Toast.makeText(this, "当前学期未就绪，稍后重试", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val schoolId = intent.getStringExtra(EXTRA_SCHOOL_ID)
        val selected = JwSchoolCatalog.findById(schoolId)
        if (selected == null) {
            Toast.makeText(this, "未识别到学校信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        school = selected
        resolvedLoginUrl = school.loginUrl
        resolvedScheduleUrl = resolveScheduleUrl(school)

        repository = CourseRepository(AppDatabase.getInstance(this).courseDao())

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_jw_import)
        toolbar.setNavigationOnClickListener { finish() }
        val schoolNameView = toolbar.findViewById<TextView>(R.id.tv_toolbar_school)
        schoolNameView.text = school.name
        schoolNameView.isSelected = true

        webView = findViewById(R.id.webview_jw)
        progressBar = findViewById(R.id.progress_loading)
        importFab = findViewById(R.id.fab_import_jw)

        setupWebView()

        importFab.setOnClickListener { handleImport() }

        if (resolvedLoginUrl.isNotBlank()) {
            webView.loadUrl(resolvedLoginUrl)
        } else {
            Toast.makeText(this, "暂无可用的登录地址", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@JwImportActivity, "页面加载失败，请检查网络", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun handleImport() {
        val url = webView.url
        if (isLoginPage(url)) {
            Toast.makeText(this, "请先完成教务系统登录", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isSchedulePage(url)) {
            showScheduleGuideDialog()
            return
        }
        val progressDialog = showImportProgress()
        webView.evaluateJavascript(EXTRACT_SCRIPT) { value ->
            val payload = decodeJsResult(value)
            if (payload.isBlank()) {
                progressDialog.dismiss()
                Toast.makeText(this, "未检测到课程数据，请确认已打开课表页面", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }
            try {
                val result = JwScheduleParser.parse(payload, semesterId)
                progressDialog.dismiss()
                if (result.courses.isEmpty()) {
                    Toast.makeText(this, "未解析到课程数据，请确认课表页面加载完整", Toast.LENGTH_LONG).show()
                    return@evaluateJavascript
                }
                confirmAndImportCourses(result.courses, result.skippedCount)
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this, "解析失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showScheduleGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("进入课表页面")
            .setMessage("请先在教务系统中进入个人课表页面，再点击导入。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun confirmAndImportCourses(courses: List<Course>, skippedCount: Int) {
        val filtered = CourseImportHelper.filterConflicts(courses)
        if (filtered.conflictCount > 0 || filtered.duplicateCount > 0) {
            val message = "检测到${filtered.conflictCount}条时间冲突、${filtered.duplicateCount}条重复课程，将自动跳过冲突项。"
            AlertDialog.Builder(this)
                .setTitle("导入冲突提示")
                .setMessage(message)
                .setPositiveButton("继续导入") { _, _ ->
                    applyImportCourses(filtered, skippedCount)
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            applyImportCourses(filtered, skippedCount)
        }
    }

    private fun applyImportCourses(filtered: ConflictFilterResult, skippedCount: Int) {
        val progressDialog = showImportProgress()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.replaceAll(semesterId, filtered.courses)
                }
                progressDialog.dismiss()
                val skipped = CourseImportHelper.totalSkipped(skippedCount, filtered)
                val message = "导入完成：成功${filtered.courses.size}条，跳过${skipped}条"
                Toast.makeText(this@JwImportActivity, message, Toast.LENGTH_LONG).show()
                ScheduleWidgetProvider.requestUpdate(this@JwImportActivity)
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@JwImportActivity, "导入失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showImportProgress(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("导入课表")
            .setMessage("正在导入，请稍候...")
            .setView(ProgressBar(this))
            .setCancelable(false)
            .show()
    }

    private fun isLoginPage(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        if (url.contains("login", ignoreCase = true)) return true
        val loginBase = resolvedLoginUrl.substringBefore("?")
        return loginBase.isNotBlank() && url.startsWith(loginBase, ignoreCase = true)
    }

    private fun isSchedulePage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.contains("xskbcx", ignoreCase = true) || url.contains("/kbcx/")) return true
        val scheduleBase = resolvedScheduleUrl.substringBefore("?")
        return scheduleBase.isNotBlank() && url.startsWith(scheduleBase, ignoreCase = true)
    }

    private fun resolveScheduleUrl(school: JwSchool): String {
        if (school.scheduleUrl.isNotBlank()) return school.scheduleUrl
        val loginUrl = school.loginUrl
        if (loginUrl.isBlank()) return ""
        val marker = "/xtgl/"
        val base = if (loginUrl.contains(marker)) {
            loginUrl.substringBefore(marker).trimEnd('/') + "/"
        } else {
            loginUrl.substringBeforeLast("/").trimEnd('/') + "/"
        }
        return base + "kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
    }

    private fun decodeJsResult(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var result = value.trim()
        if (result == "null" || result == "undefined") return ""
        if (result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
            result = result.substring(1, result.length - 1)
            result = result.replace("\\\\", "\\")
            result = result.replace("\\\"", "\"")
            result = result.replace("\\n", "\n")
            result = result.replace("\\t", "\t")
            result = result.replace("\\r", "\r")
        }
        return result
    }

    companion object {
        const val EXTRA_SEMESTER_ID = "extra_semester_id"
        const val EXTRA_SCHOOL_ID = "extra_school_id"

        private val EXTRACT_SCRIPT = """
            (function() {
              try {
                if (window.kbList && Array.isArray(window.kbList)) {
                  return JSON.stringify({source:'kbList', items: window.kbList});
                }
                if (window.kbxx && Array.isArray(window.kbxx)) {
                  return JSON.stringify({source:'kbxx', items: window.kbxx});
                }
              } catch (e) {}
              function textOf(node) {
                if (!node) return '';
                return (node.innerText || node.textContent || '').trim();
              }
              function extractFromListTable() {
                var table = document.querySelector('#kblist_table');
                if (!table) return [];
                var items = [];
                var bodies = table.querySelectorAll("tbody[id^='xq_']");
                bodies.forEach(function(body) {
                  var dayText = textOf(body.querySelector('span.week'));
                  var lastPeriodText = '';
                  var rows = body.querySelectorAll('tr');
                  rows.forEach(function(row) {
                    var periodCell = row.querySelector("td[id^='jc_']");
                    var periodText = textOf(periodCell);
                    if (periodText) {
                      lastPeriodText = periodText;
                    } else if (lastPeriodText) {
                      periodText = lastPeriodText;
                    }
                    var courseNodes = row.querySelectorAll('div.timetable_con');
                    courseNodes.forEach(function(node) {
                      var name = textOf(node.querySelector('.title'));
                      var raw = textOf(node);
                      var weekText = '';
                      var locationText = '';
                      var teacherText = '';
                      var fonts = node.querySelectorAll('font');
                      fonts.forEach(function(font) {
                        var text = textOf(font);
                        if (!text) return;
                        if (font.querySelector('.glyphicon-calendar')) {
                          weekText = text;
                        }
                        if (font.querySelector('.glyphicon-map-marker')) {
                          locationText = text;
                        }
                        if (font.querySelector('.glyphicon-user')) {
                          teacherText = text;
                        }
                      });
                      items.push({
                        source: 'kblist',
                        name: name,
                        raw: raw,
                        dayText: dayText,
                        periodText: periodText,
                        weekText: weekText,
                        location: locationText,
                        teacher: teacherText
                      });
                    });
                  });
                });
                return items;
              }
              var listItems = extractFromListTable();
              if (listItems.length) {
                return JSON.stringify({source:'kblist', items: listItems});
              }
              var nodes = Array.from(document.querySelectorAll(".timetable_con"));
              var items = nodes.map(function(node) {
                var cell = node.closest('td');
                var cellIndex = cell ? cell.cellIndex : -1;
                var rowSpan = cell ? cell.rowSpan : 1;
                var rowHeader = '';
                if (cell && cell.parentElement && cell.parentElement.cells && cell.parentElement.cells.length > 1) {
                  rowHeader = textOf(cell.parentElement.cells[1]);
                }
                var name = textOf(node.querySelector('.title'));
                var raw = textOf(node);
                var timeText = '';
                var locationText = '';
                var teacherText = '';
                var pList = node.querySelectorAll('p');
                pList.forEach(function(p) {
                  var labelSpan = p.querySelector('span[title]');
                  var title = labelSpan ? labelSpan.getAttribute('title') : '';
                  var text = textOf(p);
                  if (title && title.indexOf('节/周') >= 0) {
                    timeText = text;
                  } else if (title && title.indexOf('上课地点') >= 0) {
                    locationText = text;
                  } else if (title && title.indexOf('教师') >= 0) {
                    teacherText = text;
                  }
                });
                return {
                  source: 'grid',
                  name: name,
                  raw: raw,
                  cellIndex: cellIndex,
                  rowSpan: rowSpan,
                  rowHeader: rowHeader,
                  timeText: timeText,
                  location: locationText,
                  teacher: teacherText
                };
              });
              return JSON.stringify({source:'dom', items: items});
            })();
        """.trimIndent()
    }
}
