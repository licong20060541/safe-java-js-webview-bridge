package cn.pedant.SafeWebViewBridge;

import android.text.TextUtils;
import android.webkit.WebView;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;

/**
 * 核心类
 */
public class JsCallJava {
    private final static String TAG = "JsCallJava";
    private final static String RETURN_RESULT_FORMAT = "{\"code\": %d, \"result\": %s}";
    private HashMap<String, Method> mMethodsMap;
    private String mInjectedName; // "HostApp"
    private String mPreloadInterfaceJS;
    private Gson mGson;

    // 1 create JsCallJava
    public JsCallJava (String injectedName, Class injectedCls) {
        try {
            if (TextUtils.isEmpty(injectedName)) {
                throw new Exception("injected name can not be null");
            }
            mInjectedName = injectedName;
            mMethodsMap = new HashMap<String, Method>();
            //!!获取自身声明的所有方法（包括public private protected）， getMethods会获得所有继承与非继承的方法
            Method[] methods = injectedCls.getDeclaredMethods();
            // javascript:(function(b){...})(window) 定义并立即执行
            StringBuilder sb = new StringBuilder("javascript:(function(b){console.log(\"");
            sb.append(mInjectedName);
            // Array.prototype.slice.call 将具有length属性的对象转成数组
            sb.append(" initialization begin\");var a={queue:[],callback:function(){var d=Array.prototype.slice.call(arguments,0);var c=d.shift();var e=d.shift();this.queue[c].apply(this,d);if(!e){delete this.queue[c]}}};");
            for (Method method : methods) {
                String sign;
                if (method.getModifiers() != (Modifier.PUBLIC | Modifier.STATIC) || (sign = genJavaMethodSign(method)) == null) {
                    continue;
                }
                mMethodsMap.put(sign, method);
                sb.append(String.format("a.%s=", method.getName())); // 连续写等号了
            }

            sb.append("function(){var f=Array.prototype.slice.call(arguments,0);if(f.length<1){throw\"");
            sb.append(mInjectedName);
            sb.append(" call error, message:miss method name\"}var e=[];for(var h=1;h<f.length;h++){var c=f[h];var j=typeof c;e[e.length]=j;if(j==\"function\"){var d=a.queue.length;a.queue[d]=c;f[h]=d}}var g=JSON.parse(prompt(JSON.stringify({method:f.shift(),types:e,args:f})));if(g.code!=200){throw\"");
            sb.append(mInjectedName);
            sb.append(" call error, code:\"+g.code+\", message:\"+g.result}return g.result};Object.getOwnPropertyNames(a).forEach(function(d){var c=a[d];if(typeof c===\"function\"&&d!==\"callback\"){a[d]=function(){return c.apply(a,[d].concat(Array.prototype.slice.call(arguments,0)))}}});b.");
            sb.append(mInjectedName);
            sb.append("=a;console.log(\""); // 见下面格式化的，赋值给window.HostApp = a;
            sb.append(mInjectedName);
            sb.append(" initialization end\")})(window);");
            mPreloadInterfaceJS = sb.toString();
        } catch(Exception e){
            Log.e(TAG, "init js error:" + e.getMessage());
        }
    }

// #########################################################
//  2  格式化后的输出
//    javascript: (function(b) {
//        console.log("HostApp initialization begin");
//        var a = {
//                queue: [],
//                callback: function() {
//                     // arr.slice(3); 从索引3开始到结束,下面从0开始slice，arguments不是数组(类数组)因此转换
//                    var d = Array.prototype.slice.call(arguments, 0);//获取该函数参数并转换为Array数组
//                    var c = d.shift();//取得数组第一个元素并且从d中删除
//                    var e = d.shift();
//                    this.queue[c].apply(this, d); // 我的理解是调用queue[c]的函数，里面用到的this为a，参数为d
//                    if(!e) {// e为空的时候，将queue数组属性名称为c的对象删除
//                        delete this.queue[c]
//                    }
//                }
//        };
//        //各种赋值，最后都等于同一个函数!!!!
//        a.alert = a.alert = a.alert = a.delayJsCallBack = a.getIMSI = a.getOsSdk
//          = a.goBack = a.overloadMethod = a.overloadMethod = a.passJson2Java = a.passLongType
//          = a.retBackPassJson = a.retJavaObject = a.testLossTime = a.toast = a.toast
//          = function() {
//            var f = Array.prototype.slice.call(arguments, 0);
//            if(f.length < 1) {
//                throw "HostApp call error, message:miss method name"
//            }
//            var e = [];
//            //此段判断，然后赋值
//            for(var h = 1; h < f.length; h++) {
//                var c = f[h];
//                var j = typeof c;
//                e[e.length] = j;
//                if(j == "function") {
//                    var d = a.queue.length;
//                    a.queue[d] = c; // 给queue[]添加函数,如下demo2！！！
//                    f[h] = d // 修改变量值f[h]为在queue中的序号，这样一来，类型为函数，但值为序号
//                }
//            }
//            //将匿名对象{method: f.shift(),types: e,args: f}转换成json字符串并用浏览器弹出确认可输入框，然后取得输入框的值json序列化为js对象
//            var g = JSON.parse(prompt(JSON.stringify({ // 调用下面的call函数执行操作
//                    method: f.shift(), //弹出一个，然后e和f的个数匹配了
//                    types: e,
//                    args: f
//            })));
//            if(g.code != 200) {
//                throw "HostApp call error, code:" + g.code + ", message:" + g.result
//            }
//            return g.result
//        };
//        //获取a的属性值，然后循环
//        Object.getOwnPropertyNames(a).forEach(function(d) {
//            var c = a[d]; // c即上面的alert等函数
//            //判断赋值
//            if(typeof c === "function" && d !== "callback") {
//                a[d] = function() { // 重新赋值函数，但里面的实现调用的还是老的函数逻辑
//                    //concat 连接两个数组， 调用alert等函数，其中里面this为a， 参数为[d, arguments]
//                    return c.apply(a, [d].concat(Array.prototype.slice.call(arguments, 0)))
//                }
//            }
//        });
//        b.HostApp = a;
//        console.log("HostApp initialization end")
//    })(window);//闭包函数默认执行，然后赋给window。这样window.b就可以执行了 b.HostApp就是执行a的内容，但是a具体处理逻辑不对外开放，避免外部污染a内部逻辑


    // js调用 demo1
    // 1. <button onclick="HostApp.toast('我是气泡');">测试</button>
    // 2. 调用a.toast('我是气泡')
    // 3. c.apply(a, [d].concat(Array.prototype.slice.call(arguments, 0)))修改参数为[toast, '我是气泡']
    // 4. 调用function() {} -- 调用java call函数 -- 反射调用获得结果
    // 5. return g.result

    // cb 异步回调，传入js函数到Java方法,设定3秒后回调 demo2
    // <button onclick="HostApp.delayJsCallBack(3, 'call back haha', function (msg) {HostApp.alert(msg);});">测试</button>
    // 见下demo2


    private String genJavaMethodSign (Method method) {
        String sign = method.getName();
        Class[] argsTypes = method.getParameterTypes();
        int len = argsTypes.length;
        if (len < 1 || argsTypes[0] != WebView.class) {
            Log.w(TAG, "method(" + sign + ") must use webview to be first parameter, will be pass");
            return null;
        }
        for (int k = 1; k < len; k++) {
            Class cls = argsTypes[k];
            if (cls == String.class) {
                sign += "_S";
            } else if (cls == int.class ||
                cls == long.class ||
                cls == float.class ||
                cls == double.class) {
                sign += "_N";
            } else if (cls == boolean.class) {
                sign += "_B";
            } else if (cls == JSONObject.class) {
                sign += "_O";
            } else if (cls == JsCallback.class) {
                sign += "_F";
            } else {
                sign += "_P";
            }
        }
        return sign;
    }

    // 2 onProgressChanged, view.loadUrl(mJsCallJava.getPreloadInterfaceJS())
    public String getPreloadInterfaceJS () {
        return mPreloadInterfaceJS;
    }

    // 3 onJsPrompt, result.confirm(mJsCallJava.call(view, message));
    public String call(WebView webView, String jsonStr) {
        if (!TextUtils.isEmpty(jsonStr)) {
            try {
                JSONObject callJson = new JSONObject(jsonStr);
                String methodName = callJson.getString("method");
                JSONArray argsTypes = callJson.getJSONArray("types");
                JSONArray argsVals = callJson.getJSONArray("args");
                String sign = methodName;
                int len = argsTypes.length();
                Object[] values = new Object[len + 1];
                int numIndex = 0;
                String currType;

                values[0] = webView;

                for (int k = 0; k < len; k++) {
                    currType = argsTypes.optString(k);
                    if ("string".equals(currType)) {
                        sign += "_S";
                        values[k + 1] = argsVals.isNull(k) ? null : argsVals.getString(k);
                    } else if ("number".equals(currType)) {
                        sign += "_N";
                        numIndex = numIndex * 10 + k + 1;
                    } else if ("boolean".equals(currType)) {
                        sign += "_B";
                        values[k + 1] = argsVals.getBoolean(k);
                    } else if ("object".equals(currType)) {
                        sign += "_O";
                        values[k + 1] = argsVals.isNull(k) ? null : argsVals.getJSONObject(k);
                    } else if ("function".equals(currType)) {//!!!demo2
                        sign += "_F"; // 处理函数回调，统一修改为JsCallback, mInjectedName = "HostApp"
                        // CALLBACK_JS_FORMAT = "javascript:%s.callback(%d, %d %s);";
                        // String.format(CALLBACK_JS_FORMAT, mInjectedName, mIndex, mIsPermanent, sb.toString());
                        // 即"javascript:HostApp.callback(argsVals.getInt(k), true，'你好');"
                        // argsVals.getInt(k)为在queue中的序号，类型为函数，但值为序号
                        // 即调用a.callback函数且真正的函数存储于queue中
                        // 即调用function (msg) {HostApp.alert(msg)函数
                        values[k + 1] = new JsCallback(webView, mInjectedName, argsVals.getInt(k));
                    } else {
                        sign += "_P";
                    }
                }

                Method currMethod = mMethodsMap.get(sign);

                // 方法匹配失败
                if (currMethod == null) {
                    return getReturn(jsonStr, 500, "not found method(" + sign + ") with valid parameters");
                }
                // 数字类型细分匹配
                if (numIndex > 0) {
                    Class[] methodTypes = currMethod.getParameterTypes();
                    int currIndex;
                    Class currCls;
                    while (numIndex > 0) {
                        currIndex = numIndex - numIndex / 10 * 10;
                        currCls = methodTypes[currIndex];
                        if (currCls == int.class) {
                            values[currIndex] = argsVals.getInt(currIndex - 1);
                        } else if (currCls == long.class) {
                            //WARN: argsJson.getLong(k + defValue) will return a bigger incorrect number
                            values[currIndex] = Long.parseLong(argsVals.getString(currIndex - 1));
                        } else {
                            values[currIndex] = argsVals.getDouble(currIndex - 1);
                        }
                        numIndex /= 10;
                    }
                }

                return getReturn(jsonStr, 200, currMethod.invoke(null, values));
            } catch (Exception e) {
                //优先返回详细的错误信息
                if (e.getCause() != null) {
                    return getReturn(jsonStr, 500, "method execute error:" + e.getCause().getMessage());
                }
                return getReturn(jsonStr, 500, "method execute error:" + e.getMessage());
            }
        } else {
            return getReturn(jsonStr, 500, "call data empty");
        }
    }

    private String getReturn (String reqJson, int stateCode, Object result) {
        String insertRes;
        if (result == null) {
            insertRes = "null";
        } else if (result instanceof String) {
            result = ((String) result).replace("\"", "\\\"");
            insertRes = "\"" + result + "\"";
        } else if (!(result instanceof Integer)
                && !(result instanceof Long)
                && !(result instanceof Boolean)
                && !(result instanceof Float)
                && !(result instanceof Double)
                && !(result instanceof JSONObject)) {    // 非数字或者非字符串的构造对象类型都要序列化后再拼接
            if (mGson == null) {
                mGson = new Gson();
            }
            insertRes = mGson.toJson(result);
        } else {  //数字直接转化
            insertRes = String.valueOf(result);
        }
        String resStr = String.format(RETURN_RESULT_FORMAT, stateCode, insertRes);
        Log.d(TAG, mInjectedName + " call json: " + reqJson + " result:" + resStr);
        return resStr;
    }
}
