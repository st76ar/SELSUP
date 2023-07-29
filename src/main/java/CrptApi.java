import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final long timeInterval;    //интервал времени
    private final Semaphore semaphore;
    // Аутентификационный токен
    // Для корректной работы метода createDocument предварительно нужно получить токен для формирования правильного заголовка
    // Токен возвращается в ответ на POST запрос с подписанной УКЭП строкой
    // Необходимо также учитывать тот факт, что время жизни токена ограничено 10 часами
    String authToken = "your_auth_token";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeInterval = timeUnit.toMillis(1);
        this.semaphore = new Semaphore(requestLimit);
    }

    // Метод создания документа для ввода в оборот товара, произведенного в РФ
    public void createDocument(Object document, String signature) throws InterruptedException, IOException {
        // Попытка получить разрешение на отправку запроса
        semaphore.acquire();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Преобразуем объект документа в JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String documentJson = objectMapper.writeValueAsString(document);
            // Формирование JSON-тела запроса
            String jsonBody = "{" +
                    "\"document_format\": \"MANUAL\"," +
                    "\"product_document\": \"" + documentJson + "\"," +
                    "\"signature\": \"" + signature + "\"," +
                    "\"type\": \"LP_INTRODUCE_GOODS\"" +
                    "}";

            // Создаем HTTP запрос
            HttpPost httpPost = new HttpPost("https://ismp.crpt.ru/api/v3");

            // Устанавливаем заголовки
            httpPost.addHeader("Authorization", "Bearer " + authToken);

            // Устанавливаем тело запроса в виде JSON-строки
            StringEntity entity = new StringEntity(jsonBody);
            httpPost.setEntity(entity);

            // Отправляем запрос и получаем ответ
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // Обрабатываем ответ
                HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    String responseBody = EntityUtils.toString(responseEntity);

                    // Парсим JSON-ответ с помощью ObjectMapper
                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                    // Проверяем наличие поля "value" в JSON-ответе
                    if (jsonNode.has("value")) {
                        // Если есть поле "value", то успех
                        String documentId = jsonNode.get("value").asText();
                        System.out.println("Успешный ответ. Уникальный идентификатор документа: " + documentId);
                    } else if (jsonNode.has("code") && jsonNode.has("error_message") && jsonNode.has("description")) {
                        // Если есть поля "code", "error_message" и "description", то выводим сообщение об ошибке
                        String errorCode = jsonNode.get("code").asText();
                        String errorMessage = jsonNode.get("error_message").asText();
                        String errorDescription = jsonNode.get("description").asText();
                        System.out.println("Ошибка: " + errorCode + ", " + errorMessage + ", " + errorDescription);
                    } else {
                        // Если JSON-ответ не содержит ни поля "value", ни полей для ошибки, выводим сообщение об ошибке неизвестного формата
                        System.out.println("Ошибка: Неизвестный формат JSON-ответа");
                    }
                } else {
                    // Обработка случая, когда responseEntity равен null
                    System.out.println("Ошибка: Ответ от сервера пустой.");
                }
            } catch (IOException e) {
                // Обработка исключения
                e.printStackTrace();
            }

        } finally {
            // Освобождаем разрешение после выполнения запроса
            semaphore.release();
            // Ожидание для ограничения запросов в заданном интервале
            Thread.sleep(timeInterval);
        }
    }
}