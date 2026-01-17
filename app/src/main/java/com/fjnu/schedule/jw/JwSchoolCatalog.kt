package com.fjnu.schedule.jw

object JwSchoolCatalog {

    private val schoolList = listOf(
        JwSchool(
            id = "fjnu",
            name = "福建师范大学",
            loginUrl = "https://jwglxt.fjnu.edu.cn/jwglxt/xtgl/login_slogin.html",
            scheduleUrl = "https://jwglxt.fjnu.edu.cn/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default"
        ),
        JwSchool(
            id = "bdu",
            name = "保定学院",
            loginUrl = "http://jwgl.bdu.edu.cn/xtgl/login_slogin.html",
            scheduleUrl = ""
        ),
        JwSchool(
            id = "yau",
            name = "延安大学",
            loginUrl = "http://jwglxt.yau.edu.cn/jwglxt/xtgl/login_slogin.html",
            scheduleUrl = ""
        ),
        JwSchool(
            id = "xcc",
            name = "西昌学院",
            loginUrl = "https://jwxt.xcc.edu.cn/xtgl/login_slogin.html",
            scheduleUrl = ""
        ),
        JwSchool(
    id = "aiit",
    name = "安徽信息工程学院",
    loginUrl = "http://teach.aiit.edu.cn/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "ahau",
    name = "安徽农业大学",
    loginUrl = "http://newjwxt.ahau.edu.cn/jwglxt",
    scheduleUrl = ""
),
JwSchool(
    id = "ahjzu",
    name = "安徽建筑大学",
    loginUrl = "http://219.231.0.156/",
    scheduleUrl = ""
),
JwSchool(
    id = "buct",
    name = "北京化工大学",
    loginUrl = "http://jwglxt.buct.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "bzmc",
    name = "滨州医学院",
    loginUrl = "http://jwgl.bzmc.edu.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "czmec",
    name = "常州机电职业技术学院",
    loginUrl = "http://jwc.czmec.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "dzu",
    name = "德州学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "fjut",
    name = "福建工程学院",
    loginUrl = "https://jwxtwx.fjut.edu.cn/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "gzu",
    name = "广州大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "gxu",
    name = "广西大学",
    loginUrl = "http://jwxt2018.gxu.edu.cn/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "gxxj",
    name = "广西大学行健文理学院",
    loginUrl = "http://210.36.24.21:9017/jwglxt/xtgl",
    scheduleUrl = ""
),
JwSchool(
    id = "glit",
    name = "硅湖职业技术学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "gufe",
    name = "贵州财经大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "hzau",
    name = "华中农业大学",
    loginUrl = "http://jwgl.hzau.edu.cn/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "ccnu",
    name = "华中师范大学",
    loginUrl = "http://one.ccnu.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "scut",
    name = "华南理工大学",
    loginUrl = "http://xsjw2018.scuteo.com",
    scheduleUrl = ""
),
JwSchool(
    id = "hebtu",
    name = "河北师范大学",
    loginUrl = "http://jwgl.hebtu.edu.cn/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "helc",
    name = "河北政法职业学院",
    loginUrl = "http://jwxt.helc.edu.cn/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "hebuee",
    name = "河北环境工程学院",
    loginUrl = "http://jw.hebuee.edu.cn/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "huel",
    name = "河南财经政法大学",
    loginUrl = "http://xk.huel.edu.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "hnnu",
    name = "淮南师范学院",
    loginUrl = "http://211.70.176.173/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "hbtcm",
    name = "湖北中医药大学",
    loginUrl = "http://jwxt.hbtcm.edu.cn/jwglxt/xtgl",
    scheduleUrl = ""
),
JwSchool(
    id = "hbeutc",
    name = "湖北工程学院新技术学院",
    loginUrl = "http://jwglxt.hbeutc.cn:20000/jwglxt/xtgl",
    scheduleUrl = ""
),
JwSchool(
    id = "hbnu",
    name = "湖北师范大学",
    loginUrl = "http://jwxt.hbnu.edu.cn/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "hbue",
    name = "湖北经济学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "hzu",
    name = "贺州学院",
    loginUrl = "http://jwglxt.hzu.gx.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "hgnu",
    name = "黄冈师范学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "jlju",
    name = "吉林建筑大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "zjxu",
    name = "嘉兴学院南湖学院",
    loginUrl = "http://jwzx.zjxu.edu.cn/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "jsetc",
    name = "江苏工程职业技术学院",
    loginUrl = "http://tyjw.tmu.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "jcit",
    name = "江苏建筑职业技术学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "jxutcm",
    name = "江西中医药大学",
    loginUrl = "http://jwxt.jxutcm.edu.cn/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "ujn",
    name = "济南大学",
    loginUrl = "http://jwgl4.ujn.edu.cn/jwglxt",
    scheduleUrl = ""
),
JwSchool(
    id = "jngc",
    name = "济南工程职业技术学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "lnjdp",
    name = "辽宁机电职业技术学院",
    loginUrl = "http://jwgl.lnjdp.com/",
    scheduleUrl = ""
),
JwSchool(
    id = "mnnu",
    name = "闽南师范大学",
    loginUrl = "http://222.205.160.107/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "btts",
    name = "内蒙古科技大学包头师范学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "ncc",
    name = "南京城市职业学院",
    loginUrl = "http://jw.ncc.edu.cn/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "njtech",
    name = "南京工业大学",
    loginUrl = "https://jwgl.njtech.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "nsdzb",
    name = "南京师范大学中北学院",
    loginUrl = "http://222.192.5.246/",
    scheduleUrl = ""
),
JwSchool(
    id = "njts",
    name = "南京特殊教育师范学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "ncvt",
    name = "南宁职业技术学院",
    loginUrl = "http://jwxt.ncvt.net:8088/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "nbut",
    name = "宁波工程学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "qdbhu",
    name = "青岛滨海学院",
    loginUrl = "http://jwgl.qdbhu.edu.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "qust",
    name = "青岛科技大学",
    loginUrl = "https://jw.qust.edu.cn/jwglxt.htm",
    scheduleUrl = ""
),
JwSchool(
    id = "sju",
    name = "三江学院",
    loginUrl = "http://jw.sju.edu.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "suse",
    name = "四川轻化工大学",
    loginUrl = "http://61.139.105.138/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "sdau",
    name = "山东农业大学",
    loginUrl = "http://xjw.sdau.edu.cn/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "sdups",
    name = "山东政法大学",
    loginUrl = "http://114.214.79.176/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "sdut",
    name = "山东理工大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "sdyu",
    name = "山东青年政治学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "sjzc",
    name = "石家庄学院",
    loginUrl = "http://jwgl.sjzc.edu.cn/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "safc",
    name = "苏州农业职业技术学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "tjus",
    name = "天津体育学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "thxy",
    name = "无锡太湖学院",
    loginUrl = "http://jwcnew.thxy.org/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "wsyu",
    name = "武昌首义学院",
    loginUrl = "http://syjw.wsyu.edu.cn/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "wtu",
    name = "武汉纺织大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "whpu",
    name = "武汉轻工大学",
    loginUrl = "http://jwglxt.whpu.edu.cn/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "wmu",
    name = "温州医科大学",
    loginUrl = "http://jwxt.wmu.edu.cn",
    scheduleUrl = ""
),
JwSchool(
    id = "sdwfvc",
    name = "潍坊职业学院",
    loginUrl = "http://jwgl.sdwfvc.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "xynu",
    name = "信阳师范学院",
    loginUrl = "http://jwc.xynu.edu.cn/jxzhxxfwpt.htm",
    scheduleUrl = ""
),
JwSchool(
    id = "xmut",
    name = "厦门理工学院",
    loginUrl = "http://jw.xmut.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "xzmc",
    name = "徐州医科大学",
    loginUrl = "http://222.193.95.102/",
    scheduleUrl = ""
),
JwSchool(
    id = "swu",
    name = "西南大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "swupl",
    name = "西南政法大学",
    loginUrl = "http://njwxt.swupl.edu.cn/jwglxt/xtgl",
    scheduleUrl = ""
),
JwSchool(
    id = "swun",
    name = "西南民族大学",
    loginUrl = "http://jwxt.swun.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "xupt",
    name = "西安邮电大学",
    loginUrl = "http://www.zfjw.xupt.edu.cn/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "cmu",
    name = "中国医科大学",
    loginUrl = "http://jw.cmu.edu.cn/jwglxt/xtgl/login_slogin.html",
    scheduleUrl = ""
),
JwSchool(
    id = "cug",
    name = "中国地质大学（武汉）",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "cumt",
    name = "中国矿业大学",
    loginUrl = "http://jwxt.cumt.edu.cn/jwglxt/",
    scheduleUrl = ""
),
JwSchool(
    id = "cumtxh",
    name = "中国矿业大学徐海学院",
    loginUrl = "http://xhjw.cumt.edu.cn:8080/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "zafu",
    name = "浙江农林大学",
    loginUrl = "http://115.236.84.158/xtgl",
    scheduleUrl = ""
),
JwSchool(
    id = "zjut",
    name = "浙江工业大学",
    loginUrl = "http://www.gdjw.zjut.edu.cn/",
    scheduleUrl = ""
),
JwSchool(
    id = "zjgsu",
    name = "浙江工商大学",
    loginUrl = "http://124.160.64.163/jwglxt/xtgl/",
    scheduleUrl = ""
),
JwSchool(
    id = "zjnu",
    name = "浙江师范大学",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "zjxz",
    name = "浙江师范大学行知学院",
    loginUrl = "",
    scheduleUrl = ""
),
JwSchool(
    id = "zufe",
    name = "浙江财经大学",
    loginUrl = "http://fzjh.zufe.edu.cn/jwglxt",
    scheduleUrl = ""
)
    )

    fun list(): List<JwSchool> = schoolList

    fun findById(id: String?): JwSchool? {
        if (id.isNullOrBlank()) return null
        return schoolList.firstOrNull { it.id == id }
    }
}
