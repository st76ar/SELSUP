import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class CrptApi {
    private final int requestLimit;     //предел кол-ва запросов в течении заданного интервала времени
    private final long timeInterval;    //интервал времени
    private final Lock lock = new ReentrantLock();
    private int requestCount;           //счетчик запросов
    private long lastRequestTime;       //время последнего запроса
    BlockingQueue<Object> queue;

//    private long remainingTime;         //нам нужно знать сколько времени осталось до конца интервала,
//                                        // чтобы на это время усыплять поток, если лимит кол-ва запросов превышен

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeInterval = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.requestCount = 0;
        this.lastRequestTime = 0;
//        this.remainingTime = 0;
        queue = new ArrayBlockingQueue<>(requestLimit);
    }

    // Метод для создания документа для ввода в оборот товара, произведенного в РФ
    public void createDocument(Object document, String signature) throws InterruptedException, JsonProcessingException {
        lock.lock();
        try {
            long currentTime = System.currentTimeMillis();

            // Уменьшаем оставшееся время до конца текущего интервала
            remainingTime -= currentTime - lastRequestTime;
            lastRequestTime = currentTime;

            if (remainingTime <= 0) {
                // Если интервал истек, сбрасываем счетчик запросов и оставшееся время
                requestCount = 0;
                remainingTime = timeInterval;
            }

            if (requestCount >= requestLimit) {
                // Если превышен лимит запросов, усыпляем поток на оставшееся время до конца интервала
                Thread.sleep(remainingTime);
                lastRequestTime = 0; // Обновляем lastRequestTime, чтобы начать новый интервал
                requestCount = 0;
                remainingTime = timeInterval;
            }

            // Здесь выполняется создание документа и отправка запроса к API
            // document - Java объект с данными документа
            // signature - строка с подписью
            // ...
            // Преобразуем объект документа в JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String documentJson = objectMapper.writeValueAsString(document);

            // Создаем HTTP клиент
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost("https://ismp.crpt.ru/api/v3");


            requestCount++;
        } finally {
            lock.unlock();
        }
    }

}
