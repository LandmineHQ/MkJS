package mortar.kubejs.mkjs.net;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MkHTTP {
    // 线程池用于执行异步请求
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 执行GET请求
     * 
     * @param urlString 请求URL
     * @return 响应内容
     */
    public String get(String urlString) throws Exception {
        return sendRequest(urlString, "GET", null, null);
    }

    /**
     * 执行带有headers的GET请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @return 响应内容
     */
    public String get(String urlString, Map<String, String> headers) throws Exception {
        return sendRequest(urlString, "GET", headers, null);
    }

    /**
     * 执行POST请求
     * 
     * @param urlString 请求URL
     * @param jsonData  JSON格式数据
     * @return 响应内容
     */
    public String post(String urlString, String jsonData) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return sendRequest(urlString, "POST", headers, jsonData);
    }

    /**
     * 执行带有headers的POST请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @param jsonData  JSON格式数据
     * @return 响应内容
     */
    public String post(String urlString, Map<String, String> headers, String jsonData) throws Exception {
        if (headers == null) {
            headers = new HashMap<>();
        }
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", "application/json");
        }
        return sendRequest(urlString, "POST", headers, jsonData);
    }

    /**
     * 异步执行GET请求
     * 
     * @param urlString 请求URL
     * @return 包含响应内容的CompletableFuture
     */
    public CompletableFuture<String> getAsync(String urlString) {
        return sendRequestAsync(urlString, "GET", null, null);
    }

    /**
     * 异步执行带有headers的GET请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @return 包含响应内容的CompletableFuture
     */
    public CompletableFuture<String> getAsync(String urlString, Map<String, String> headers) {
        return sendRequestAsync(urlString, "GET", headers, null);
    }

    /**
     * 异步执行POST请求
     * 
     * @param urlString 请求URL
     * @param jsonData  JSON格式数据
     * @return 包含响应内容的CompletableFuture
     */
    public CompletableFuture<String> postAsync(String urlString, String jsonData) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return sendRequestAsync(urlString, "POST", headers, jsonData);
    }

    /**
     * 异步执行带有headers的POST请求
     * 
     * @param urlString 请求URL
     * @param headers   请求头
     * @param jsonData  JSON格式数据
     * @return 包含响应内容的CompletableFuture
     */
    public CompletableFuture<String> postAsync(String urlString, Map<String, String> headers, String jsonData) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", "application/json");
        }
        return sendRequestAsync(urlString, "POST", headers, jsonData);
    }

    /**
     * 发送HTTP请求（同步）
     */
    private String sendRequest(String urlString, String method, Map<String, String> headers, String data)
            throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);

        // 设置请求头
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        // 发送数据
        if (data != null && !data.isEmpty()) {
            connection.setDoOutput(true);
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                byte[] postData = data.getBytes(StandardCharsets.UTF_8);
                outputStream.write(postData);
                outputStream.flush();
            }
        }

        // 获取响应
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        connection.disconnect();
        return response.toString();
    }

    /**
     * 发送HTTP请求（异步）
     * 
     * @param urlString 请求URL
     * @param method    HTTP方法 (GET, POST等)
     * @param headers   请求头
     * @param data      请求数据
     * @return 包含响应内容的CompletableFuture
     */
    private CompletableFuture<String> sendRequestAsync(String urlString, String method,
            Map<String, String> headers, String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendRequest(urlString, method, headers, data);
            } catch (Exception e) {
                throw new RuntimeException("异步HTTP请求失败", e);
            }
        }, executor);
    }

    /**
     * 关闭HTTP客户端及其线程池
     * 应在应用程序关闭时调用此方法以释放资源
     */
    public void shutdown() {
        executor.shutdown();
    }
}