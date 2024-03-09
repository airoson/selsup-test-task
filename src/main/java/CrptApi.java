import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CrptApi {
    private final Semaphore semaphore;
    private final Logger logger = Logger.getLogger(CrptApi.class.getName());
    private final long durationMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit){
        durationMillis = timeUnit.toMillis(1);
        semaphore = new Semaphore(requestLimit);
    }

    public int callApi(Document document, String sign){
        try{
            semaphore.acquire();
            new Thread(() -> {
                try{
                    Thread.sleep(durationMillis);
                    semaphore.release();
                }catch (InterruptedException e){
                    logger.severe("Thread was executed when waiting for blocking to release: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }).start();
            logger.info("Calling API: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss")));
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(document);
            URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            con.connect();
            return con.getResponseCode();
        }catch (InterruptedException e){
            logger.severe("Thread was interrupted when waiting for call execution: " + e.getMessage());
            Thread.currentThread().interrupt();
        }catch(JsonProcessingException e){
            logger.log(Level.SEVERE, "Can't serialize JSON: %s".formatted(e.getMessage()));
            throw new IllegalArgumentException("Invalid document");
        }catch(Exception e){
            logger.log(Level.SEVERE, e.getMessage());
        }
        return 0;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document{
        private Description description;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;
        @JsonProperty("doc_type")
        private String docType;
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        @JsonSerialize(using = DateSerializer.class)
        private LocalDate productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<Product> products;
        @JsonProperty("reg_date")
        @JsonSerialize(using = DateSerializer.class)
        private LocalDate reg_date;
        @JsonProperty("reg_number")
        private String regNumber;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Product{
        @JsonProperty("certification_document")
        private String certificationDocument;
        @JsonProperty("certification_document_date")
        @JsonSerialize(using = DateSerializer.class)
        private LocalDate certificationDocumentDate;
        @JsonProperty("certification_document_number")
        private String certificationDocumentNumber;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        @JsonSerialize(using = DateSerializer.class)
        private LocalDate productionDate;
        @JsonProperty("tnved_code")
        private String tnvedCode;
        @JsonProperty("uit_code")
        private String uitCode;
        @JsonProperty("uitu_code")
        private String uituCode;
    }

    @Data
    public static class Description{
        private String participantInn;

        public Description(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class DateSerializer extends StdSerializer<LocalDate>{
        public DateSerializer(){
            this(null);
        }
        protected DateSerializer(Class<LocalDate> t) {
            super(t);
        }
        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            gen.writeString(value.format(formatter));
        }
    }
}
