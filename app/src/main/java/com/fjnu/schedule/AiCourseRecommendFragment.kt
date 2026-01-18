package com.fjnu.schedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class AiCourseRecommendFragment : Fragment() {
    private lateinit var btnGenerateRecommend: View
    private lateinit var courseRecommendations: View
    private lateinit var courseInput: EditText
    private lateinit var courseListContainer: LinearLayout
    private lateinit var btnClearResults: View
    private lateinit var btnResearch: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ai_course_recommend, container, false)
        
        // 初始化UI元素
        btnGenerateRecommend = view.findViewById(R.id.btn_generate_recommend)
        courseRecommendations = view.findViewById(R.id.course_recommendations)
        courseInput = view.findViewById(R.id.course_input)
        courseListContainer = view.findViewById(R.id.course_list_container)
        btnClearResults = view.findViewById(R.id.btn_clear_results)
        btnResearch = view.findViewById(R.id.btn_research)
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置按钮点击事件
        btnGenerateRecommend.setOnClickListener {
            // 获取用户输入的课程名
            val courseName = courseInput.text.toString().trim()
            if (courseName.isEmpty()) {
                // 显示提示信息
                view.findViewById<TextView>(R.id.input_hint)?.text = "请输入课程名称"
                return@setOnClickListener
            }
            
            // 调用API获取推荐课程
            fetchRecommendedCourses(courseName)
        }

        // 设置清空结果按钮点击事件
        btnClearResults.setOnClickListener {
            clearResults()
        }

        // 设置重新搜索按钮点击事件
        btnResearch.setOnClickListener {
            val courseName = courseInput.text.toString().trim()
            if (courseName.isEmpty()) {
                view.findViewById<TextView>(R.id.input_hint)?.text = "请输入课程名称"
                return@setOnClickListener
            }
            fetchRecommendedCourses(courseName)
        }

        // 初始化推荐课程列表为隐藏
        courseRecommendations.visibility = View.GONE
    }

    private fun clearResults() {
        // 清空课程列表
        courseListContainer.removeAllViews()
        
        // 隐藏推荐课程区域
        courseRecommendations.visibility = View.GONE
        
        // 显示提示信息
        view?.findViewById<TextView>(R.id.input_hint)?.text = "结果已清空，请输入新的课程名称"
        
        // 恢复按钮状态
        (btnGenerateRecommend as? android.widget.Button)?.text = "生成推荐课程"
        btnGenerateRecommend.isEnabled = true
    }

    private fun fetchRecommendedCourses(courseName: String) {
        // 显示加载状态
        activity?.runOnUiThread {
            (btnGenerateRecommend as? android.widget.Button)?.text = "生成中..."
            btnGenerateRecommend.isEnabled = false
            view?.findViewById<TextView>(R.id.input_hint)?.text = "正在搜索推荐课程..."
        }

        // 在后台线程中执行网络请求
        thread {
            try {
                // 构建API请求
                val apiUrl = URL("https://open.bigmodel.cn/api/paas/v4/web_search")
                val connection = apiUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer 9211c7e70b214430b942056380bcc7f4.tM8ggEu5WBNg1wd7")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // 构建请求体
                val searchQuery = "我是一名学生，现在我需要在网上寻找学习资料课程，请你给我推荐一些b站上的高质量网课，我需要的课程是$courseName"
                val requestBody = """{
  "search_query": "$searchQuery",
  "search_engine": "search_pro_sogou",
  "search_intent": true,
  "count": 10,
  "search_domain_filter": "https://www.bilibili.com/",
  "search_recency_filter": "oneYear",
  "content_size": "medium",
  "request_id": "android_app_${System.currentTimeMillis()}",
  "user_id": "student_user"
}""".trimIndent()

                // 发送请求
                val outputStream: OutputStream = connection.outputStream
                outputStream.write(requestBody.toByteArray())
                outputStream.flush()
                outputStream.close()

                // 读取响应
                val responseCode = connection.responseCode
                val reader = if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader(InputStreamReader(connection.inputStream))
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream))
                }

                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                connection.disconnect()

                // 解析响应并更新UI
                val responseJson = response.toString()
                activity?.runOnUiThread {
                    parseAndDisplayCourses(responseJson)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    // 显示错误信息
                    view?.findViewById<TextView>(R.id.input_hint)?.text = "网络请求失败，请重试"
                    (btnGenerateRecommend as? android.widget.Button)?.text = "生成推荐课程"
                    btnGenerateRecommend.isEnabled = true
                }
            }
        }
    }

    private fun parseAndDisplayCourses(responseJson: String) {
        try {
            // 解析JSON响应
            val courses = mutableListOf<Course>()
            
            // 简单的JSON解析（实际项目中建议使用Gson或Jackson）
            if (responseJson.contains("search_result")) {
                val searchResultStart = responseJson.indexOf("search_result") + 14
                if (searchResultStart < responseJson.length) {
                    val searchResultEnd = responseJson.lastIndexOf("}")
                    if (searchResultEnd > searchResultStart) {
                        val searchResultJson = responseJson.substring(searchResultStart, searchResultEnd)
                        
                        // 提取每个课程的title和link
                        var startIndex = 0
                        while (startIndex < searchResultJson.length) {
                            val titleStart = searchResultJson.indexOf("title", startIndex)
                            if (titleStart == -1) break
                            
                            val titleValueStart = searchResultJson.indexOf("\"", titleStart + 7) + 1
                            if (titleValueStart <= 0) break
                            
                            val titleValueEnd = searchResultJson.indexOf("\"", titleValueStart)
                            if (titleValueEnd <= titleValueStart) break
                            
                            val title = searchResultJson.substring(titleValueStart, titleValueEnd)
                            
                            val linkStart = searchResultJson.indexOf("link", titleValueEnd)
                            if (linkStart == -1) break
                            
                            val linkValueStart = searchResultJson.indexOf("\"", linkStart + 6) + 1
                            if (linkValueStart <= 0) break
                            
                            val linkValueEnd = searchResultJson.indexOf("\"", linkValueStart)
                            if (linkValueEnd <= linkValueStart) break
                            
                            val link = searchResultJson.substring(linkValueStart, linkValueEnd)
                            
                            courses.add(Course(title, link))
                            startIndex = linkValueEnd
                        }
                    }
                }
            }

            // 清空之前的课程列表
            courseListContainer.removeAllViews()

            // 显示新的课程列表
            if (courses.isNotEmpty()) {
                for (course in courses) {
                    val courseItem = layoutInflater.inflate(R.layout.item_course_link, courseListContainer, false)
                    courseItem.findViewById<TextView>(R.id.course_title)?.text = course.title
                    courseItem.findViewById<TextView>(R.id.course_link)?.setOnClickListener {
                        openCourseLink(course.link)
                    }
                    courseListContainer.addView(courseItem)
                }
                courseRecommendations.visibility = View.VISIBLE
                view?.findViewById<TextView>(R.id.input_hint)?.text = "点击课程链接查看详情"
            } else {
                view?.findViewById<TextView>(R.id.input_hint)?.text = "未找到相关课程推荐"
                courseRecommendations.visibility = View.GONE
            }

        } catch (e: Exception) {
            e.printStackTrace()
            view?.findViewById<TextView>(R.id.input_hint)?.text = "解析数据失败，请重试"
        } finally {
            // 恢复按钮状态
            (btnGenerateRecommend as? android.widget.Button)?.text = "重新生成推荐"
            btnGenerateRecommend.isEnabled = true
        }
    }

    private fun openCourseLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context?.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // 显示错误信息
            view?.findViewById<TextView>(R.id.input_hint)?.text = "打开链接失败，请手动复制链接"
        }
    }

    // 课程数据类
    data class Course(val title: String, val link: String)
}
