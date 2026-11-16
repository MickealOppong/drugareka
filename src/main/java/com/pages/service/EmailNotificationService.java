package com.pages.service;


import com.pages.dto.EmailProductItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;



@Slf4j
@Service
public class EmailNotificationService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;

    @Value("${resend.api-key}")
    private String resendApiKey;


    @Value("${app.frontend-url}")
    private String frontEndUrl;

    @Value("${delivery.confirmation.url}")
    private String deliveryConfirmationUrl;

    @Value("${shipment.confirmation.url}")
    private String shipmentConfirmationUrl;

    @Value("${return.confirmation.url}")
    private String returnConfirmationUrl;

    @Value("${password.reset.url}")
    private String passwordResetUrl;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name:kasoa.pl}")
    private String fromName;

    public EmailNotificationService() {
        this.restTemplate = new RestTemplate();
    }


    /**
     * PASSWORD RESET LINK
     * @param email
     * @param token
     */
    @Async
    public void sendPasswordResetEmail(String email, String token) {

        String resetLink = UriComponentsBuilder
                .fromUriString(passwordResetUrl)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();

        String subject = "Resetowanie hasła do konta Kasoa.pl";

        String htmlContent = """
<h2 style="margin:0 0 20px; font-size:22px;
                                               line-height:1.4; color:#182016;">
                                        Resetowanie hasła (Reset your password)
                                    </h2>
<p style="margin:0 0 16px; font-size:15px;
                                              line-height:1.8; color:#182016;">
                                        Otrzymaliśmy prośbę o zresetowanie hasła do Twojego konta w serwisie Kasoa.pl.
                                        (We received a request to reset the password for your Kasoa.pl account.)
                                    </p>
<p style="margin:0 0 24px; font-size:15px;
                                              line-height:1.8; color:#182016;">
                                        Kliknij poniższy przycisk, aby ustawić nowe hasło.
                                        (Click the button below to create a new password.)
                                    </p>



<a href="%s" style="display:inline-block; padding:15px 28px;
                                                          font-size:15px; font-weight:bold;
                                                          color:#ffffff; text-decoration:none;
                                                          border-radius:6px;">
                                                    Zresetuj hasło (Reset password)
                                                </a>






<p style="margin:0 0 14px; font-size:14px;
                                                          line-height:1.8; color:#182016;">
<strong>Ważne:</strong> Link do resetowania
                                                    hasła wygaśnie po 24 godzinach.
                                                    Jeśli nie prosiłeś(-aś) o zmianę hasła,
                                                    zignoruj tę wiadomość. Twoje hasło
                                                    pozostanie bez zmian.
                                                </p>
<p style="margin:0; font-size:14px;
                                                          line-height:1.8; color:#182016;">
                                                    (This password reset link expires in 24 hours.
                                                    If you did not request a password reset,
                                                    you can ignore this email. Your password
                                                    will remain unchanged.)
                                                </p>
        """.formatted(escapeHtml(resetLink));

        sendHtmlEmail(email, subject, buildEmailLayout(htmlContent));
    }


    /**
     * Wraps email-specific content in the shared Kasoa email layout.
     * Header, body and footer are rows in the same 600px presentation table.
     */
    private String buildEmailLayout(String bodyContent) {
        return """
        <!DOCTYPE html>
        <html lang="pl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
        </head>
        <body style="margin:0; padding:30px 12px; background-color:#F5F6F1; font-family:Arial,Helvetica,sans-serif; color:#182016;">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="width:100%%; background-color:#F5F6F1;">
                <tr>
                    <td align="center">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%%; max-width:600px; table-layout:fixed; border-collapse:collapse;">
                            <tr>
                                <td width="600" align="center" style="width:100%%; background-color:#68764B; padding:28px 30px; text-align:center; box-sizing:border-box;">
                                    <div style="font-size:26px; line-height:1; font-weight:800; color:#FFFFFF;">
                                        kasoa<span style="font-weight:400;">.pl</span>
                                    </div>
                                    <div style="margin-top:10px; font-size:12px; line-height:18px; color:#E9EDDF; letter-spacing:0.5px;">
                                        DRUGA RĘKA · BEZPIECZNE ZAKUPY
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td width="600" style="width:100%%; background-color:#FFFFFF; padding:32px 30px; box-sizing:border-box;">
                                    %s
                                </td>
                            </tr>
                            <tr>
                                <td width="600" style="width:100%%; background-color:#182016; padding:24px 30px; text-align:center; box-sizing:border-box;">
                                    <div style="font-size:17px; line-height:24px; font-weight:800; color:#FFFFFF;">
                                        kasoa<span style="font-weight:400; color:#AEB69D;">.pl</span>
                                    </div>
                                    <div style="margin-top:7px; font-size:11px; line-height:18px; color:#AEB69D;">
                                        Dobre rzeczy. Drugie życie. (Good things. A second chance.)
                                    </div>
                                    <div style="margin-top:14px; font-size:10px; line-height:1.7; color:#AEB69D;">
                                        Ta wiadomość została wygenerowana automatycznie.
                                        <br>
                                        Status zamówienia możesz śledzić w zakładce „Zamówienia”.
                                        <br><br>
                                        KASAWA Sp. z o.o. · ul. Kostromska nr 55 lok. 95, 97-300 Piotrków Trybunalski, Polska
                                        <br>
                                        KRS: 0001269876 · NIP: 7712946929
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </body>
        </html>
        """.formatted(bodyContent);
    }


    /**
     * Sends an HTML email through the Resend HTTPS API.
     *
     * This deliberately does not use SMTP, because Railway Hobby blocks
     * outbound SMTP connections. All communication with Resend is HTTPS.
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(resendApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = Map.of(
                    "from", fromName + " <" + fromEmail + ">",
                    "to", List.of(to),
                    "subject", subject,
                    "html", htmlContent
            );

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    RESEND_API_URL,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(
                        "Successfully dispatched Kasoa notification email to target: {}",
                        to
                );
            } else {
                log.error(
                        "Resend returned HTTP {} while sending email to {}. Response: {}",
                        response.getStatusCode().value(),
                        to,
                        response.getBody()
                );
            }

        } catch (HttpStatusCodeException e) {
            log.error(
                    "Resend rejected email to {}. HTTP {}. Response: {}",
                    to,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString(),
                    e
            );
        } catch (RestClientException e) {
            log.error(
                    "Failed to connect to Resend while sending email to {}",
                    to,
                    e
            );
        } catch (Exception e) {
            log.error(
                    "Unexpected error while sending email to {} through Resend",
                    to,
                    e
            );
        }
    }


    /**
     * Sends a consolidated, itemized confirmation receipt to the Buyer for multiple products.
     */
    @Async
    public void sendBulkOrderConfirmationToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            List<EmailProductItemDto> items,
            java.math.BigDecimal totalAmount
    ) {
        String subject = "🎉 Twoje zamówienie na kasoa.pl zostało opłacone! #" + orderNumber;

        // Build order items
        StringBuilder itemsHtml = new StringBuilder();

        if (items != null) {
            for (EmailProductItemDto item : items) {
                String productName = escapeHtml(item.getProductName());
                String sellerName = escapeHtml(item.getSellerName());

                String amount = item.getAmount() != null
                        ? item.getAmount().toPlainString()
                        : "0.00";

                String quantity = item.getQuantity() != null
                        ? item.getQuantity().toString()
                        : "1";

                String shippingCost = item.getShippingCost() != null
                        ? item.getShippingCost().toPlainString()
                        : "0.00";

                String serviceCharge = item.getServiceCharge() != null
                        ? item.getServiceCharge().toPlainString()
                        : "0.00";


                itemsHtml.append("""
                <tr>
                    <td colspan="2" style="
                        padding: 16px 0 8px 0;
                        border-bottom: 1px dashed #DDE2D8;
                    ">
                        <div style="
                            font-size: 14px;
                            line-height: 1.5;
                            color: #182016;
                            font-weight: 700;
                        ">
                            %s
                        </div>

                        <div style="
                            margin-top: 6px;
                            font-size: 12px;
                            line-height: 1.8;
                            color: #667066;
                        ">
                            Sprzedawca:
                            <strong style="color: #182016;">%s</strong>
                            <br>
                            Status sprzedawcy:
                            <strong>Osoba prywatna</strong>
                            <br>
                            Ilość: <strong>%s</strong>
                        </div>
                    </td>
                </tr>

                <tr>
                    <td style="
                        padding: 8px 0;
                        font-size: 12px;
                        color: #667066;
                    ">
                        Cena produktu
                    </td>
                    <td align="right" style="
                        padding: 8px 0;
                        font-size: 13px;
                        color: #182016;
                        white-space: nowrap;
                    ">
                        %s zł
                    </td>
                </tr>

                <tr>
                    <td style="
                        padding: 6px 0;
                        font-size: 12px;
                        color: #667066;
                    ">
                        Koszt dostawy
                    </td>
                    <td align="right" style="
                        padding: 6px 0;
                        font-size: 13px;
                        color: #182016;
                        white-space: nowrap;
                    ">
                        %s zł
                    </td>
                </tr>

                <tr>
                    <td style="
                        padding: 6px 0 16px 0;
                        font-size: 12px;
                        color: #667066;
                        border-bottom: 1px dashed #DDE2D8;
                    ">
                        Opłata serwisowa
                    </td>
                    <td align="right" style="
                        padding: 6px 0 16px 0;
                        font-size: 13px;
                        color: #182016;
                        border-bottom: 1px dashed #DDE2D8;
                        white-space: nowrap;
                    ">
                        %s zł
                    </td>
                </tr>
                """.formatted(
                        productName,
                        sellerName,
                        escapeHtml(quantity),
                        escapeHtml(amount),
                        escapeHtml(shippingCost),
                        escapeHtml(serviceCharge)
                ));
            }
        }

        String total = totalAmount != null
                ? totalAmount.toPlainString()
                : "0.00";

        String htmlContent = """
✓ Płatność potwierdzona (Payment confirmed)
                    
<h1 style="
                        margin: 18px 0 10px 0;
                        font-size: 25px;
                        line-height: 1.25;
                        color: #182016;
                        font-weight: 800;
                        letter-spacing: -0.5px;
                    ">
                        Dziękujemy za zakupy, %s!
                    </h1>
<p style="
                        margin: 0 0 26px 0;
                        font-size: 15px;
                        line-height: 1.7;
                        color: #667066;
                    ">
                        Twoja płatność za zamówienie
                        <strong style="color: #182016;">#%s</strong>
                        została pomyślnie przetworzona.
                        Poniżej znajdziesz szczegóły zamówienia,
                        dane sprzedawców oraz informacje o podmiocie
                        obsługującym transakcję.
                    </p>




<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 7px;
                                ">
                                    Numer zamówienia
                                </div>
<div style="
                                    font-family: monospace;
                                    font-size: 17px;
                                    font-weight: 700;
                                    color: #182016;
                                ">
                                    #%s
                                </div>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                ">
                                    Podsumowanie zamówienia
                                </div>





                                    %s
                                








                                            Łączna kwota zamówienia
                                        

                                            %s zł
                                        









<div style="
                                    font-size: 14px;
                                    font-weight: 800;
                                    color: #182016;
                                    margin-bottom: 9px;
                                ">
                                    Informacje o sprzedawcach
                                </div>
<p style="
                                    margin: 0;
                                    font-size: 13px;
                                    line-height: 1.7;
                                    color: #667066;
                                ">
                                    Produkty w tym zamówieniu są oferowane
                                    przez sprzedawców wskazanych przy
                                    poszczególnych pozycjach. W przypadku
                                    sprzedawcy oznaczonego jako osoba
                                    prywatna sprzedaż odbywa się pomiędzy
                                    kupującym a tym sprzedawcą.
                                    Kasoa.pl nie jest sprzedawcą tych
                                    produktów wyłącznie z tytułu
                                    udostępniania platformy.
                                </p>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 10px;
                                ">
                                    Podmiot obsługujący transakcję
                                </div>
<div style="
                                    font-size: 14px;
                                    font-weight: 800;
                                    line-height: 1.5;
                                    color: #182016;
                                ">
                                    KASAWA SPÓŁKA Z OGRANICZONĄ
                                    ODPOWIEDZIALNOŚCIĄ
                                </div>

<p style="
                                    margin: 12px 0 0 0;
                                    font-size: 12px;
                                    line-height: 1.7;
                                    color: #667066;
                                ">
                                    KASAWA Sp. z o.o. prowadzi platformę
                                    Kasoa.pl i pełni funkcję podmiotu
                                    obsługującego transakcję.
                                    Spółka nie jest sprzedawcą produktów
                                    wymienionych w tym zamówieniu.
                                </p>




<div style="
                        margin-top: 28px;
                        padding-top: 24px;
                        border-top: 1px solid #E8EBE5;
                    ">
<div style="
                            font-size: 13px;
                            font-weight: 800;
                            color: #182016;
                            margin-bottom: 14px;
                        ">
                            Co dalej?
                        </div>
<table border="0" cellpadding="0" cellspacing="0" width="100%%">
<tr>
<td style="padding-bottom: 13px;" valign="top" width="32">
<div style="
                                        width: 24px;
                                        height: 24px;
                                        line-height: 24px;
                                        text-align: center;
                                        background-color: #68764B;
                                        color: #FFFFFF;
                                        border-radius: 50%%;
                                        font-size: 11px;
                                        font-weight: 700;
                                    ">1</div>
</td>
<td style="
                                    padding: 3px 0 13px 8px;
                                    font-size: 13px;
                                    line-height: 1.5;
                                    color: #667066;
                                ">
                                    Sprzedawcy przygotują produkty
                                    do wysyłki.
                                </td>
</tr>
<tr>
<td style="padding-bottom: 13px;" valign="top" width="32">
<div style="
                                        width: 24px;
                                        height: 24px;
                                        line-height: 24px;
                                        text-align: center;
                                        background-color: #68764B;
                                        color: #FFFFFF;
                                        border-radius: 50%%;
                                        font-size: 11px;
                                        font-weight: 700;
                                    ">2</div>
</td>
<td style="
                                    padding: 3px 0 13px 8px;
                                    font-size: 13px;
                                    line-height: 1.5;
                                    color: #667066;
                                ">
                                    Otrzymasz informacje o wysyłce
                                    i numerach śledzenia, gdy będą dostępne.
                                </td>
</tr>
<tr>
<td valign="top" width="32">
<div style="
                                        width: 24px;
                                        height: 24px;
                                        line-height: 24px;
                                        text-align: center;
                                        background-color: #68764B;
                                        color: #FFFFFF;
                                        border-radius: 50%%;
                                        font-size: 11px;
                                        font-weight: 700;
                                    ">3</div>
</td>
<td style="
                                    padding: 3px 0 0 8px;
                                    font-size: 13px;
                                    line-height: 1.5;
                                    color: #667066;
                                ">
                                    Śledź przesyłki i odbierz swoje zakupy.
                                </td>
</tr>
</table>
</div>

<div style="margin-top: 28px; text-align: center;">
<a href="https://www.kasoa.pl" style="
                               display: inline-block;
                               padding: 14px 28px;
                               background-color: #68764B;
                               color: #FFFFFF;
                               text-decoration: none;
                               border-radius: 9px;
                               font-size: 13px;
                               font-weight: 700;
                           ">
                            Sprawdź moje zamówienie →
                        </a>
</div>
        """.formatted(
                escapeHtml(buyerName),
                escapeHtml(orderNumber),
                escapeHtml(orderNumber),
                itemsHtml.toString(),
                escapeHtml(total)
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }

    /**
     * Notify buyer hwne seller cancels order
     * @param buyerEmail
     * @param orderNumber
     * @param product
     * @param amount
     */

    @Async
    public void notifyBuyerOfOrderItemCancellation(
            String buyerEmail,
            String orderNumber,
            String product,
            java.math.BigDecimal amount
    ) {
        String subject = "Anulowanie produktu z zamówienia #" + orderNumber;

        String formattedAmount = amount != null
                ? amount.toPlainString()
                : "0.00";

        String htmlContent = """
Anulowanie produktu
                
<h1 style="
                    margin: 18px 0 10px 0;
                    font-size: 25px;
                    line-height: 1.25;
                    color: #182016;
                    font-weight: 800;
                ">
                    Produkt został anulowany
                </h1>
<p style="
                    margin: 0 0 25px 0;
                    font-size: 14px;
                    line-height: 1.8;
                    color: #667066;
                ">
                    Informujemy, że sprzedający anulował poniższy
                    produkt z Twojego zamówienia.
                </p>




<div style="
                                font-size: 10px;
                                font-weight: 700;
                                color: #8A9288;
                                text-transform: uppercase;
                                letter-spacing: 1px;
                                margin-bottom: 7px;
                            ">
                                Numer zamówienia
                            </div>
<div style="
                                font-family: monospace;
                                font-size: 17px;
                                font-weight: 700;
                                color: #182016;
                            ">
                                #%s
                            </div>







<div style="
                                font-size: 10px;
                                font-weight: 700;
                                color: #8A9288;
                                text-transform: uppercase;
                                letter-spacing: 1px;
                                margin-bottom: 10px;
                            ">
                                Anulowany produkt
                            </div>
<div style="
                                font-size: 15px;
                                font-weight: 700;
                                line-height: 1.6;
                                color: #182016;
                            ">
                                %s
                            </div>
<div style="
                                margin-top: 12px;
                                font-size: 12px;
                                color: #B54735;
                                font-weight: 700;
                            ">
                                Status: Anulowano przez sprzedającego (Cancelled by seller)
                            </div>







<div style="
                                font-size: 10px;
                                font-weight: 700;
                                color: #8A9288;
                                text-transform: uppercase;
                                letter-spacing: 1px;
                                margin-bottom: 8px;
                            ">
                                Kwota zwrotu
                            </div>
<div style="
                                font-size: 25px;
                                font-weight: 800;
                                color: #68764B;
                            ">
                                %s zł
                            </div>




<p style="
                    margin: 24px 0 0 0;
                    font-size: 13px;
                    line-height: 1.8;
                    color: #667066;
                ">
                    Przepraszamy za niedogodności.
                    Zwrot środków zostanie zlecony na pierwotną
                    metodę płatności.
                </p>
<p style="
                    margin: 12px 0 0 0;
                    font-size: 13px;
                    line-height: 1.8;
                    color: #667066;
                ">
                    Pozostałe produkty z Twojego zamówienia
                    nie są objęte tym anulowaniem.
                    Szczegóły możesz sprawdzić w zakładce
                    „Zamówienia” na swoim koncie Kasoa.
                </p>

<div style="margin-top: 28px; text-align: center;">
<a href="https://www.kasoa.pl" style="
                            display: inline-block;
                            padding: 14px 28px;
                            background-color: #68764B;
                            color: #FFFFFF;
                            text-decoration: none;
                            border-radius: 9px;
                            font-size: 13px;
                            font-weight: 700;
                       ">
                        Sprawdź moje zamówienie →
                    </a>
</div>
        """.formatted(
                escapeHtml(orderNumber),
                escapeHtml(product),
                escapeHtml(formattedAmount)
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }




    @Async
    public void sendCollectionNotificationToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String product,
            String collectionAddress,
            String sellerContact,
            String sellerComment
    ) {
        String subject = "Odbiór osobisty potwierdzony – zamówienie #" + orderNumber;

        String safeBuyerName = escapeHtml(
                buyerName != null && !buyerName.isBlank() ? buyerName : "Kliencie"
        );

        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProduct = escapeHtml(product);

        String safeAddress = escapeHtml(collectionAddress)
                .replace("\r\n", "<br>")
                .replace("\n", "<br>");

        String safeSellerContact = escapeHtml(
                sellerContact != null && !sellerContact.isBlank()
                        ? sellerContact
                        : "Brak podanych danych kontaktowych"
        );

        String safeSellerComment = sellerComment != null && !sellerComment.isBlank()
                ? escapeHtml(sellerComment)
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                : "Sprzedawca nie dodał dodatkowych instrukcji.";

        String htmlContent = """
✓ Odbiór osobisty potwierdzony (Personal collection confirmed)
                    
<h1 style="
                        margin: 18px 0 10px;
                        font-size: 25px;
                        line-height: 1.3;
                        color: #182016;
                        font-weight: 800;
                    ">
                        Odbiór osobisty został potwierdzony, %s!
                    </h1>
<p style="
                        margin: 0 0 24px;
                        font-size: 15px;
                        line-height: 1.7;
                        color: #667066;
                    ">
                        Sprzedawca potwierdził Twoje zamówienie i przekazał
                        szczegóły dotyczące odbioru. Możesz teraz skontaktować
                        się ze sprzedawcą, aby ustalić dogodny termin.
                    </p>




<div style="
                                    font-size: 13px;
                                    font-weight: 800;
                                    color: #8A5A12;
                                    margin-bottom: 8px;
                                ">
                                    ⏰ Masz 48 godzin na odbiór
                                </div>
<div style="
                                    font-size: 13px;
                                    line-height: 1.7;
                                    color: #765B2C;
                                ">
                                    Od momentu otrzymania tej wiadomości
                                    masz 48 godzin, aby skontaktować się
                                    ze sprzedawcą, ustalić dogodny termin
                                    i odebrać zamówiony produkt.
                                </div>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 12px;
                                ">
                                    Szczegóły zamówienia
                                </div>
<div style="
                                    font-size: 13px;
                                    color: #667066;
                                    margin-bottom: 5px;
                                ">
                                    Numer zamówienia
                                </div>
<div style="
                                    font-family: monospace;
                                    font-size: 16px;
                                    font-weight: 700;
                                    color: #182016;
                                    margin-bottom: 16px;
                                ">
                                    #%s
                                </div>
<div style="
                                    font-size: 13px;
                                    color: #667066;
                                    margin-bottom: 5px;
                                ">
                                    Produkt
                                </div>
<div style="
                                    font-size: 15px;
                                    font-weight: 700;
                                    color: #182016;
                                ">
                                    %s
                                </div>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #68764B;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 12px;
                                ">
                                    📍 Adres odbioru
                                </div>
<div style="
                                    font-size: 16px;
                                    line-height: 1.7;
                                    font-weight: 700;
                                    color: #182016;
                                ">
                                    %s
                                </div>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 10px;
                                ">
                                    Kontakt ze sprzedawcą
                                </div>
<div style="
                                    font-size: 14px;
                                    line-height: 1.7;
                                    color: #182016;
                                ">
                                    %s
                                </div>







<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 10px;
                                ">
                                    Informacje od sprzedawcy
                                </div>
<div style="
                                    font-size: 13px;
                                    line-height: 1.8;
                                    color: #667066;
                                ">
                                    %s
                                </div>




<div style="
                        padding-top: 22px;
                        border-top: 1px solid #E8EBE5;
                    ">
<div style="
                            font-size: 14px;
                            font-weight: 800;
                            color: #182016;
                            margin-bottom: 12px;
                        ">
                            Co teraz zrobić?
                        </div>
<p style="
                            margin: 0 0 10px;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">1.</strong>
                            Skontaktuj się ze sprzedawcą, aby ustalić
                            dogodny termin odbioru.
                        </p>
<p style="
                            margin: 0 0 10px;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">2.</strong>
                            Odbierz produkt pod wskazanym adresem
                            w ciągu 48 godzin od otrzymania tej wiadomości.
                        </p>
<p style="
                            margin: 0;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">3.</strong>
                            Po odbiorze produktu sprzedawca zaktualizuje
                            status zamówienia. Następnie otrzymasz od
                            Kasoa.pl osobną wiadomość e-mail z instrukcją
                            potwierdzenia odbioru.
                        </p>
</div>

<div style="margin-top: 28px; text-align: center;">
<a href="https://www.kasoa.pl/account/orders" style="
                               display: inline-block;
                               padding: 14px 28px;
                               background-color: #68764B;
                               color: #FFFFFF;
                               text-decoration: none;
                               border-radius: 9px;
                               font-size: 13px;
                               font-weight: 700;
                           ">
                            Zobacz moje zamówienia →
                        </a>
</div>
        """.formatted(
                safeBuyerName,
                safeOrderNumber,
                safeProduct,
                safeAddress,
                safeSellerContact,
                safeSellerComment
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }


    /**
     *  notification to seller to update shipping status once item handed to buyer for personal delivery
     * @param sellerEmail
     * @param sellerName
     * @param orderNumber
     * @param product
     */
    @Async
    public void sendPersonalCollectionDeliveryInstructionToSeller(
            String sellerEmail,
            String sellerName,
            String orderNumber,
            String product
    ) {
        String subject = "Potwierdź przekazanie produktu – zamówienie #" + orderNumber;

        String safeSellerName = escapeHtml(
                sellerName != null && !sellerName.isBlank()
                        ? sellerName
                        : "Sprzedawco"
        );

        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProduct = escapeHtml(product);

        String htmlContent = """
ODBIÓR OSOBISTY
                    
<h1 style="
                        margin: 18px 0 10px;
                        font-size: 25px;
                        line-height: 1.3;
                        color: #182016;
                        font-weight: 800;
                    ">
                        Produkt został przekazany kupującemu, %s?
                    </h1>
<p style="
                        margin: 0 0 24px;
                        font-size: 15px;
                        line-height: 1.7;
                        color: #667066;
                    ">
                        Po osobistym przekazaniu produktu kupującemu
                        pamiętaj o zaktualizowaniu statusu zamówienia
                        na Kasoa.pl. Dzięki temu kupujący otrzyma
                        wiadomość e-mail z prośbą o potwierdzenie odbioru.
                    </p>




<div style="
                                    font-size: 10px;
                                    font-weight: 700;
                                    color: #8A9288;
                                    text-transform: uppercase;
                                    letter-spacing: 1px;
                                    margin-bottom: 12px;
                                ">
                                    Szczegóły zamówienia
                                </div>
<div style="
                                    font-size: 13px;
                                    color: #667066;
                                    margin-bottom: 5px;
                                ">
                                    Numer zamówienia
                                </div>
<div style="
                                    font-family: monospace;
                                    font-size: 16px;
                                    font-weight: 700;
                                    color: #182016;
                                    margin-bottom: 16px;
                                ">
                                    #%s
                                </div>
<div style="
                                    font-size: 13px;
                                    color: #667066;
                                    margin-bottom: 5px;
                                ">
                                    Produkt
                                </div>
<div style="
                                    font-size: 15px;
                                    font-weight: 700;
                                    color: #182016;
                                ">
                                    %s
                                </div>







<div style="
                                    font-size: 13px;
                                    font-weight: 800;
                                    color: #8A5A12;
                                    margin-bottom: 8px;
                                ">
                                    Wymagane działanie
                                </div>
<div style="
                                    font-size: 13px;
                                    line-height: 1.7;
                                    color: #765B2C;
                                ">
                                    Zaloguj się na swoje konto Kasoa.pl
                                    i przejdź do szczegółów zamówienia.
                                    W sekcji wysyłki zaktualizuj status
                                    zamówienia na
                                    <strong>„Dostarczono” (DELIVERED)</strong>.
                                </div>




<div style="
                        padding-top: 22px;
                        border-top: 1px solid #E8EBE5;
                    ">
<div style="
                            font-size: 14px;
                            font-weight: 800;
                            color: #182016;
                            margin-bottom: 14px;
                        ">
                            Co stanie się dalej?
                        </div>
<p style="
                            margin: 0 0 12px;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">1.</strong>
                            Po zmianie statusu na „Dostarczono” kupujący
                            otrzyma od Kasoa.pl osobną wiadomość e-mail
                            z bezpiecznym linkiem do potwierdzenia odbioru.
                        </p>
<p style="
                            margin: 0 0 12px;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">2.</strong>
                            Kupujący musi skorzystać z otrzymanego linku
                            i potwierdzić, że odebrał produkt.
                        </p>
<p style="
                            margin: 0;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
<strong style="color: #182016;">3.</strong>
                            Po potwierdzeniu odbioru przez kupującego
                            Kasoa.pl rozpocznie proces realizacji wypłaty
                            należnych Ci środków.
                        </p>
</div>




<div style="
                                    font-size: 12px;
                                    line-height: 1.7;
                                    color: #667066;
                                ">
<strong style="color: #182016;">
                                        Ważne:
                                    </strong>
                                    Status „Dostarczono” aktualizuj
                                    wyłącznie po faktycznym przekazaniu
                                    produktu kupującemu. Sama zmiana
                                    statusu nie oznacza jeszcze
                                    potwierdzenia odbioru przez kupującego.
                                </div>




<div style="margin-top: 28px; text-align: center;">
<a href="https://www.kasoa.pl/account/orders" style="
                               display: inline-block;
                               padding: 14px 28px;
                               background-color: #68764B;
                               color: #FFFFFF;
                               text-decoration: none;
                               border-radius: 9px;
                               font-size: 13px;
                               font-weight: 700;
                           ">
                            Przejdź do moich zamówień →
                        </a>
</div>
        """.formatted(
                safeSellerName,
                safeOrderNumber,
                safeProduct
        );

        sendHtmlEmail(sellerEmail, subject, buildEmailLayout(htmlContent));
    }



    /**
     * REFUND CONFIRMATION TO BUYER
     */
    public void sendOrderCancellationRefundEmailToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName,
            BigDecimal totalAmount
    ) {
        String subject = "Zamówienie #" + orderNumber
                + " anulowane — zwrot płatności";

        String safeBuyerName = escapeHtml(
                buyerName != null && !buyerName.isBlank()
                        ? buyerName
                        : "Kliencie"
        );

        String safeOrderNumber = escapeHtml(
                orderNumber != null ? orderNumber : ""
        );

        String safeProductName = escapeHtml(
                productName != null && !productName.isBlank()
                        ? productName
                        : "Produkt"
        );

        String total = totalAmount != null
                ? totalAmount.abs()
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                : "0.00";

        String htmlContent = """
ZAMÓWIENIE ANULOWANE
                        
<h1 style="
                            margin:18px 0 10px;
                            font-size:25px;
                            line-height:1.25;
                            color:#182016;
                            font-weight:800;
                        ">
                            Zamówienie zostało anulowane
                        </h1>
<p style="
                            margin:0 0 24px;
                            font-size:15px;
                            line-height:1.7;
                            color:#667066;
                        ">
                            Dzień dobry %s,
                            <br/><br/>
                            informujemy, że zamówienie
                            <strong style="color:#182016;">#%s</strong>
                            zostało anulowane, ponieważ sprzedawca
                            nie potwierdził go w wymaganym terminie.
                        </p>




<div style="
                                        font-size:10px;
                                        font-weight:700;
                                        color:#8A9288;
                                        text-transform:uppercase;
                                        letter-spacing:1px;
                                        margin-bottom:8px;
                                    ">
                                        Anulowany produkt
                                    </div>
<div style="
                                        font-size:16px;
                                        line-height:1.5;
                                        font-weight:700;
                                        color:#182016;
                                        overflow-wrap:anywhere;
                                    ">
                                        %s
                                    </div>
<div style="
                                        margin-top:22px;
                                        padding-top:18px;
                                        border-top:1px solid #E4E8DE;
                                    ">
<div style="
                                            font-size:10px;
                                            font-weight:700;
                                            color:#8A9288;
                                            text-transform:uppercase;
                                            letter-spacing:1px;
                                        ">
                                            Kwota zwrotu
                                        </div>
<div style="
                                            margin-top:8px;
                                            font-size:27px;
                                            line-height:1.3;
                                            font-weight:800;
                                            color:#68764B;
                                        ">
                                            %s zł
                                        </div>
</div>
<p style="
                                        margin:12px 0 0;
                                        font-size:13px;
                                        line-height:1.7;
                                        color:#667066;
                                    ">
                                        Zwrot zostanie przekazany na pierwotną
                                        metodę płatności. Zaksięgowanie środków
                                        może potrwać kilka dni roboczych,
                                        w zależności od banku lub operatora
                                        płatności.
                                    </p>




<p style="
                            margin:0;
                            font-size:14px;
                            line-height:1.7;
                            color:#667066;
                        ">
                            Przepraszamy za niedogodności i dziękujemy
                            za wyrozumiałość. W razie pytań możesz
                            skontaktować się z naszym zespołem.
                        </p>

<div style="margin-top:28px;text-align:center;">
<a href="https://www.kasoa.pl" style="
                                   display:inline-block;
                                   padding:14px 28px;
                                   background-color:#68764B;
                                   color:#FFFFFF;
                                   text-decoration:none;
                                   border-radius:9px;
                                   font-size:13px;
                                   font-weight:700;
                               ">
                                Odwiedź Kasoa.pl →
                            </a>
</div>
        """.formatted(
                safeBuyerName,
                safeOrderNumber,
                safeProductName,
                escapeHtml(total)
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }

    /**
     * Compiles a beautiful brand confirmation email when a product goes live.
     */
    @Async
    public void sendProductCreatedNotification(
            String sellerEmail,
            String sellerName,
            String productName,
            BigDecimal price
    ) {
        String subject = "🎉 Twój przedmiot został wystawiony na kasoa.pl!";

        String safeSellerName = escapeHtml(sellerName);
        String safeProductName = escapeHtml(productName);

        String formattedPrice = price != null
                ? price.toPlainString()
                : "0.00";

        String htmlContent = """
✓ Oferta opublikowana
                        
<h1 style="
                            margin: 20px 0 10px 0;
                            font-size: 26px;
                            line-height: 1.2;
                            color: #182016;
                            font-weight: 800;
                            letter-spacing: -0.6px;
                        ">
                            Cześć %s!
                        </h1>
<p style="
                            margin: 0 0 28px 0;
                            font-size: 15px;
                            line-height: 1.7;
                            color: #667066;
                        ">
                            Twój przedmiot został pomyślnie wystawiony
                            na <strong style="color: #68764B;">kasoa.pl</strong>.
                            Teraz może znaleźć nowego właściciela.
                        </p>




<div style="
                                        font-size: 10px;
                                        font-weight: 700;
                                        color: #8A9288;
                                        text-transform: uppercase;
                                        letter-spacing: 1px;
                                        margin-bottom: 8px;
                                    ">
                                        Twój przedmiot
                                    </div>
<div style="
                                        font-size: 17px;
                                        line-height: 1.4;
                                        font-weight: 700;
                                        color: #182016;
                                    ">
                                        %s
                                    </div>
<div style="
                                        margin-top: 12px;
                                        font-size: 23px;
                                        line-height: 1;
                                        font-weight: 800;
                                        color: #68764B;
                                    ">
                                        %s zł
                                    </div>







                                    💡
                                

<div style="
                                        font-size: 13px;
                                        font-weight: 800;
                                        color: #68764B;
                                        margin-bottom: 5px;
                                    ">
                                        Wskazówka od kasoa.pl
                                    </div>
<div style="
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #536047;
                                    ">
                                        Dobre zdjęcia i dokładny opis zwiększają
                                        szanse na szybką sprzedaż.
                                    </div>







                                    💳
                                

<div style="
                                        font-size: 13px;
                                        font-weight: 800;
                                        color: #795A20;
                                        margin-bottom: 6px;
                                    ">
                                        Przygotuj się na otrzymywanie wypłat
                                    </div>
<div style="
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #6B5A3C;
                                    ">
                                        Aby móc otrzymywać płatności za
                                        sprzedane przedmioty, ukończ
                                        weryfikację sprzedawcy, jeśli jeszcze
                                        tego nie zrobiłeś. Przejdź do
                                        <strong>
                                            Konto → Profil → Weryfikacja sprzedawcy
                                        </strong>
                                        i postępuj zgodnie z instrukcjami.
                                    </div>
<div style="
                                        margin-top: 8px;
                                        font-size: 12px;
                                        line-height: 1.5;
                                        color: #795A20;
                                        font-weight: 700;
                                    ">
                                        Warto zrobić to już teraz, aby uniknąć
                                        opóźnień w otrzymaniu wypłaty po sprzedaży.
                                    </div>




<div style="
                            margin-top: 28px;
                            padding-top: 24px;
                            border-top: 1px solid #E8EBE5;
                        ">
<div style="
                                font-size: 13px;
                                font-weight: 800;
                                color: #182016;
                                margin-bottom: 14px;
                            ">
                                Co dalej?
                            </div>
<table border="0" cellpadding="0" cellspacing="0" width="100%%">
<tr>
<td style="padding-bottom: 12px;" valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">
                                            1
                                        </div>
</td>
<td style="
                                            padding: 3px 0 12px 8px;
                                            font-size: 13px;
                                            line-height: 1.6;
                                            color: #667066;
                                        " valign="top">
                                        Poczekaj na zainteresowanego kupującego.
                                    </td>
</tr>
<tr>
<td style="padding-bottom: 12px;" valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">
                                            2
                                        </div>
</td>
<td style="
                                            padding: 3px 0 12px 8px;
                                            font-size: 13px;
                                            line-height: 1.6;
                                            color: #667066;
                                        " valign="top">
                                        Po sprzedaży otrzymasz od nas
                                        wszystkie informacje dotyczące wysyłki.
                                    </td>
</tr>
<tr>
<td valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">
                                            3
                                        </div>
</td>
<td style="
                                            padding: 3px 0 0 8px;
                                            font-size: 13px;
                                            line-height: 1.6;
                                            color: #667066;
                                        " valign="top">
                                        Przygotuj przedmiot i wyślij go
                                        do nowego właściciela.
                                    </td>
</tr>
</table>
</div>

<div style="
                            margin-top: 30px;
                            text-align: center;
                        ">
<a href="https://www.kasoa.pl" style="
                                    display: inline-block;
                                    padding: 14px 28px;
                                    background-color: #68764B;
                                    color: #FFFFFF;
                                    text-decoration: none;
                                    border-radius: 9px;
                                    font-size: 13px;
                                    font-weight: 700;
                                ">
                                Przejdź do kasoa.pl →
                            </a>
</div>
        """.formatted(
                safeSellerName,
                safeProductName,
                formattedPrice
        );

        // Send the email asynchronously.
        sendHtmlEmail(sellerEmail, subject, buildEmailLayout(htmlContent));
    }


    /**
     * Compiles a beautiful brand confirmation email when a product goes live.
     */
    @Async
    public void sendDac7RolloverUpdate(
            String sellerEmail,
            String firstName,
            int archivedSalesCount,
            BigDecimal archivedGrossVolume,
            int currentYear
    ) {
        String subject =
                "Twój roczny licznik sprzedaży kasoa.pl został zresetowany! #" + currentYear;

        String htmlContent = """
<h2 style="margin-top:0; color:#68764B;">
                    Cześć %s!
                </h2>
<p>
                    Rozpoczął się nowy rok rozliczeniowy na <strong>kasoa.pl</strong>.
                    Twój roczny licznik sprzedaży został zresetowany.
                </p>
<p>
                    Dane z poprzedniego okresu zostały zachowane:
                </p>

<p style="margin:0 0 10px;">
<strong>Sprzedaż:</strong> %d
                    </p>
<p style="margin:0;">
<strong>Łączna wartość sprzedaży:</strong> %s PLN
                    </p>

<p style="margin-top:24px;">
                    Licznik dla roku <strong>%d</strong> rozpoczyna się od zera.
                </p>
<p>
                    Dziękujemy, że korzystasz z kasoa.pl.
                </p>
        """.formatted(
                firstName,
                archivedSalesCount,
                archivedGrossVolume.toPlainString(),
                currentYear
        );

        // Executes using the background async thread pool
        sendHtmlEmail(sellerEmail, subject, buildEmailLayout(htmlContent));
    }

    @Async
    public void sendDac7AnnualResetFailureAdminEmail(
            String adminEmail,
            int currentYear,
            Exception exception
    ) {
        String subject =
                "🚨 KASOA.PL — Niepowodzenie resetu DAC7 (DAC7 reset failure) #" + currentYear;

        String errorMessage = exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getName();

        String htmlContent = """
<h2 style="margin-top:0; color:#b91c1c;">
                    🚨 Niepowodzenie rocznego resetu DAC7 (DAC7 annual reset failed)
                </h2>
<p>
                    Roczny reset roku podatkowego DAC7 dla
                    <strong>kasoa.pl</strong> zakończył się niepowodzeniem.
                </p>

<p style="margin:0 0 12px;">
<strong>Rok podatkowy (Tax year):</strong> %d
                    </p>
<p style="margin:0 0 12px;">
<strong>Status:</strong>
<span style="color:#b91c1c; font-weight:bold;">
                            KRYTYCZNY BŁĄD (Critical failure)
                        </span>
</p>
<p style="margin:0;">
<strong>Błąd (Error):</strong><br/>
                        %s
                    </p>

<p>
                    Liczniki DAC7 mogły nie zostać zresetowane.
                    Prosimy o natychmiastowe sprawdzenie aplikacji i bazy danych.

                </p>
<p style="margin-top:30px; font-size:13px; color:#777;">
                    To automatyczne powiadomienie krytyczne wygenerowane
                    przez system podatkowy kasoa.pl.
                </p>
        """.formatted(
                currentYear,
                escapeHtml(errorMessage)
        );

        sendHtmlEmail(adminEmail, subject, buildEmailLayout(htmlContent));
    }

    @Async
    public void sendDac7AnnualResetAdminEmail(
            String adminEmail,
            int modifiedRowsCount,
            int currentYear
    ) {
        String subject =
                "KASOA.PL — Roczny reset roku podatkowego DAC7 zakończony (DAC7 annual tax year reset completed) #" + currentYear;

        String htmlContent = """
<h2 style="margin-top:0; color:#68764B;">
                    Roczny reset roku podatkowego DAC7 (DAC7 annual tax year reset)
                </h2>
<p>
                    Roczne przejście do nowego roku podatkowego DAC7 dla
                    <strong>kasoa.pl</strong> zostało pomyślnie zakończone.
                </p>

<p style="margin:0 0 12px;">
<strong>Rok podatkowy (Tax year):</strong> %d
                    </p>
<p style="margin:0 0 12px;">
<strong>Zaktualizowane profile sprzedawców (Seller profiles updated):</strong> %d
                    </p>
<p style="margin:0;">
<strong>Status:</strong>
<span style="color:#4d7c0f; font-weight:bold;">
                            POWODZENIE (Success)
                        </span>
</p>

<p>
                    Liczniki podatkowe DAC7 zostały zresetowane i od teraz
                    rejestrują aktywność w nowym roku kalendarzowym.
                </p>
<p style="margin-top:30px; font-size:13px; color:#777;">
                    To automatyczne powiadomienie administracyjne wygenerowane
                    przez system podatkowy kasoa.pl.
                </p>
        """.formatted(
                currentYear,
                modifiedRowsCount
        );

        sendHtmlEmail(adminEmail, subject, buildEmailLayout(htmlContent));
    }


    /**
     * Sends a transactional confirmation email to the Buyer with Escrow protection updates.
     */
    @Async
    public void sendOrderConfirmationToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName,
            java.math.BigDecimal totalAmount,
            String sellerName) {

        String subject = "Twoje zamówienie na Kasoa.pl zostało opłacone! 🎉 #" + orderNumber;

        java.text.NumberFormat currencyFormat =
                java.text.NumberFormat.getCurrencyInstance(
                        java.util.Locale.forLanguageTag("pl-PL"));

        String formattedAmount = currencyFormat.format(totalAmount);

        String htmlContent = """
<div style="padding: 20px 0;">
<h2 style="font-size: 18px; font-weight: 700; margin: 0 0 12px 0;">
                Dziękujemy za zakupy, %s!
            </h2>
<p style="font-size: 14px; line-height: 1.6; color: #42504F;
                      margin: 0 0 20px 0;">
                Twoja płatność za zamówienie
                <strong>#%s</strong> została pomyślnie przetworzona.
                Poniżej znajdziesz podsumowanie zakupu oraz informacje
                o sprzedawcy i obsłudze transakcji.
            </p>

<div style="background-color: #F4F7F6; border-radius: 8px;
                        padding: 15px; margin-bottom: 20px;">
<span style="font-size: 11px; font-weight: 700;
                             color: #5E6C6A; display: block;
                             text-transform: uppercase; letter-spacing: 0.5px;">
                    Podsumowanie zamówienia
                </span>
<p style="font-size: 14px; color: #0D1313; margin: 8px 0;">
<strong>Numer zamówienia:</strong> #%s
                </p>
<p style="font-size: 14px; color: #0D1313; margin: 8px 0;">
<strong>Produkt:</strong> %s
                </p>
<p style="font-size: 14px; color: #0D1313; margin: 8px 0;">
<strong>Sprzedawca:</strong> %s
                </p>
<p style="font-size: 13px; color: #5E6C6A; margin: 8px 0;">
<strong>Status sprzedawcy (Seller status):</strong> Osoba prywatna (Private seller)
                </p>
<div style="border-top: 1px solid #DCE5E2; margin-top: 12px;
                            padding-top: 12px;">
<span style="font-size: 16px; font-weight: 700;
                                 color: #1E3A3A;">
                        Łączna kwota: %s
                    </span>
</div>
</div>

<div style="border: 1px solid #E0E7E6; border-radius: 8px;
                        padding: 15px; margin-bottom: 20px;">
<h3 style="font-size: 14px; color: #1E3A3A;
                           margin: 0 0 10px 0;">
                    Informacje o sprzedawcy
                </h3>
<p style="font-size: 13px; line-height: 1.6; color: #42504F;
                          margin: 0;">
                    Produkt jest sprzedawany przez wskazanego powyżej
                    sprzedawcę będącego osobą prywatną. Jest to sprzedaż
                    pomiędzy kupującym a prywatnym sprzedawcą.
                    Kasoa.pl nie jest sprzedawcą tego produktu.
                </p>
</div>



<div style="background-color: #EDF7F3; border-radius: 8px;
                        padding: 15px; margin-bottom: 20px;">
<strong style="font-size: 13px; color: #2A6B4E;
                               display: block; margin-bottom: 6px;">
                    Status płatności
                </strong>
<p style="font-size: 13px; line-height: 1.6; color: #2A6B4E;
                          margin: 0;">
                    Twoja płatność została przetworzona.
                    Sprzedawca został powiadomiony o zamówieniu
                    i może przygotować przesyłkę.
                    O kolejnych etapach realizacji zamówienia
                    poinformujemy Cię w osobnej wiadomości.
                </p>
</div>
<p style="font-size: 13px; line-height: 1.6; color: #5E6C6A;
                      margin: 0;">
                W razie pytań dotyczących zamówienia skontaktuj się z nami:
                <a href="mailto:kasawa.corp@gmail.com" style="color: #1E3A3A;">
                    kasawa.corp@gmail.com
                </a>.
            </p>
</div>
        """.formatted(
                escapeHtml(buyerName),
                escapeHtml(orderNumber),
                escapeHtml(orderNumber),
                escapeHtml(productName),
                escapeHtml(sellerName),
                escapeHtml(formattedAmount)
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }



    /**
     * Sends a notification email to the Seller instructing them to pack and ship the item.
     */
    @Async
    public void sendOrderActionToSeller(String sellerEmail, String sellerName, String orderNumber, String productName, java.math.BigDecimal payoutAmount) {
        String subject = "Twój przedmiot został sprzedany! 🚀 Przygotuj wysyłkę zamówienia #" + orderNumber;

        String htmlContent = """
<div style="padding: 20px 0;">
<h2 style="font-size: 18px; font-weight: 700; margin: 0 0 12px 0;">Świetne wieści, %s!</h2>
<p style="font-size: 14px; line-height: 1.6; color: #42504F; margin: 0 0 20px 0;">
                    Kupujący pomyślnie opłacił Twój produkt w zamówieniu <strong>#%s</strong>. Środki zostały w pełni zabezpieczone.
                </p>
<div style="background-color: #F4F7F6; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
<span style="font-size: 11px; font-weight: 700; color: #5E6C6A; display: block; text-transform: uppercase; letter-spacing: 0.5px;">Sprzedany produkt</span>
<strong style="font-size: 14px; color: #0D1313; display: block; margin-top: 4px;">%s</strong>
<strong style="font-size: 16px; color: #2A6B4E; display: block; margin-top: 4px;">Twój przewidywany zysk: %s zł</strong>
</div>
<div style="background-color: #FEF3C7; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
<strong style="font-size: 13px; color: #D97706; display: block; margin-bottom: 4px;">📦 Co musisz teraz zrobić?</strong>
<ol style="font-size: 13px; line-height: 1.5; color: #D97706; margin: 0; padding-left: 20px;">
<li>Zaloguj się do swojego panelu i przejdź do sekcji <strong>'Wysyłki'</strong>.</li>
<li>Pobierz wygenerowaną, opłaconą etykietę przewozową.</li>
<li>Bezpiecznie zapakuj produkt i nadaj paczkę w ciągu najbliższych 3 dni roboczych.</li>
</ol>
</div>
<p style="font-size: 13px; color: #5E6C6A; margin: 0;">
                    Pamiętaj, że środki zostaną automatycznie uwolnione do Twojego salda wypłat (Payouts) niezwłocznie po tym, jak kupujący potwierdzi bezpieczny odbiór paczki.
                </p>
</div>
        """.formatted(sellerName, orderNumber, productName, payoutAmount.toString());

        sendHtmlEmail(sellerEmail, subject, buildEmailLayout(htmlContent));
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    /**
     * Sends a single consolidated notification email to a Seller listing all products
     * they sold in this transaction, with a combined payout summary.
     */
    @Async
    public void sendBulkOrderActionToSeller(
            String sellerEmail,
            String sellerName,
            String orderNumber,
            List<EmailProductItemDto> products,
            String buyerAddress,
            String corporateAddress
    ) {

        String safeSellerName = escapeHtml(sellerName);
        String safeOrderNumber = escapeHtml(orderNumber);

        BigDecimal productsTotal = BigDecimal.ZERO;
        BigDecimal shippingTotal = BigDecimal.ZERO;

        StringBuilder productsHtml = new StringBuilder();

        if (products != null && !products.isEmpty()) {

            for (EmailProductItemDto item : products) {

                if (item == null) {
                    continue;
                }

                String productName = escapeHtml(
                        item.getProductName() != null
                                ? item.getProductName()
                                : "Product"
                );

                BigDecimal price = item.getAmount() != null
                        ? item.getAmount()
                        : BigDecimal.ZERO;

                BigDecimal shippingCost = item.getShippingCost() != null
                        ? item.getShippingCost()
                        : BigDecimal.ZERO;

                long quantity = item.getQuantity() != null
                        ? item.getQuantity()
                        : 1;

                BigDecimal productTotal = price.multiply(
                        BigDecimal.valueOf(quantity)
                );

                productsTotal = productsTotal.add(productTotal);
                shippingTotal = shippingTotal.add(shippingCost);

                productsHtml.append("""
                <tr>
                    <td style="
                        padding:16px 0;
                        border-bottom:1px solid #E4E7DE;
                        vertical-align:top;
                    ">
                        <div style="
                            font-size:15px;
                            line-height:22px;
                            font-weight:700;
                            color:#182016;
                        ">
                            %s
                        </div>

                        <div style="
                            margin-top:4px;
                            font-size:13px;
                            line-height:20px;
                            color:#687064;
                        ">
                            Quantity: %s
                        </div>
                    </td>

                    <td style="
                        padding:16px 0;
                        border-bottom:1px solid #E4E7DE;
                        text-align:right;
                        vertical-align:top;
                        white-space:nowrap;
                    ">
                        <div style="
                            font-size:15px;
                            line-height:22px;
                            font-weight:700;
                            color:#182016;
                        ">
                            %s PLN
                        </div>
                    </td>
                </tr>
                """.formatted(
                        productName,
                        quantity,
                        productTotal.toPlainString()
                ));
            }

        } else {

            productsHtml.append("""
            <tr>
                <td colspan="2"
                    style="
                        padding:16px 0;
                        color:#687064;
                        font-size:14px;
                    ">
                    No products available.
                </td>
            </tr>
            """);
        }

        BigDecimal orderTotal = productsTotal.add(shippingTotal);

        /*
         * Recipient address.
         *
         * buyerAddress should contain:
         *
         * Kostromska 120
         * 97-300 Piotrków Trybunalski, Poland
         */
        String recipientAddressHtml = formatAddress(
                "Recipient:",
                "KASOA.PL (Order #" + orderNumber + ")",
                buyerAddress,
                "+48 22 123 45 67 (Kasoa Support Line)"
        );

        /*
         * Sender address.
         *
         * corporateAddress can contain multiple lines.
         */
        String senderAddressHtml = formatAddress(
                "Sender:",
                "KASOA.PL",
                corporateAddress,
                "+48 22 123 45 67 (Kasoa Support Line)"
        );

        String subject =
                "Kasoa.pl — Nowe zamówienie #" + orderNumber;

        String html = """
<div style="
                                    font-size:14px;
                                    font-weight:700;
                                    color:#68764B;
                                    margin-bottom:8px;
                                ">
                                    NOWE ZAMÓWIENIE
                                </div>
<div style="
                                    font-size:25px;
                                    line-height:32px;
                                    font-weight:800;
                                    color:#182016;
                                ">
                                    Cześć %s! 👋
                                </div>
<div style="
                                    margin-top:12px;
                                    font-size:15px;
                                    line-height:24px;
                                    color:#5E6658;
                                ">
                                    Otrzymałeś nowe zamówienie. Przygotuj
                                    produkty do wysyłki zgodnie z poniższymi
                                    danymi.
                                </div>




<div style="
                                                font-size:12px;
                                                font-weight:700;
                                                color:#68764B;
                                                text-transform:uppercase;
                                            ">
                                                Numer zamówienia
                                            </div>
<div style="
                                                margin-top:4px;
                                                font-size:18px;
                                                font-weight:800;
                                                color:#182016;
                                            ">
                                                #%s
                                            </div>




<div style="
                                    margin-top:30px;
                                    font-size:18px;
                                    line-height:24px;
                                    font-weight:800;
                                    color:#182016;
                                ">
                                    Produkty
                                </div>


                                    %s

                                




                                            Produkty
                                        

                                            %s PLN
                                        



                                            Dostawa
                                        

                                            %s PLN
                                        







                                            Razem
                                        

                                            %s PLN
                                        



<div style="
                                    margin-top:32px;
                                    font-size:18px;
                                    line-height:24px;
                                    font-weight:800;
                                    color:#182016;
                                ">
                                    Dane do przesyłki
                                </div>





                                                %s

                                            
<div style="
                                                margin:16px 0;
                                                border-top:1px solid #E0E4DB;
                                                height:1px;
                                                line-height:1px;
                                            ">
                                                 
                                            </div>


                                                %s

                                            







<div style="
                                                font-size:16px;
                                                font-weight:800;
                                                color:#182016;
                                            ">
                                                📦 Co teraz?
                                            </div>
<div style="
                                                margin-top:10px;
                                                font-size:14px;
                                                line-height:22px;
                                                color:#5E6658;
                                            ">
                                                1. Przygotuj wszystkie produkty
                                                z zamówienia.
                                                <br/><br/>
                                                2. Zapakuj je bezpiecznie,
                                                aby dotarły w dobrym stanie.
                                                <br/><br/>
                                                3. Wyślij przesyłkę na podany
                                                adres odbiorcy.
                                                <br/><br/>
                                                4. Po nadaniu przesyłki
                                                zaktualizuj status wysyłki
                                                w Kasoa.pl.
                                                <br/><br/>
                                                5. Zachowaj potwierdzenie
                                                nadania przesyłki.
                                            </div>







<div style="
                                                font-size:16px;
                                                font-weight:800;
                                                color:#182016;
                                            ">
                                                💰 Płatność
                                            </div>
<div style="
                                                margin-top:8px;
                                                font-size:14px;
                                                line-height:22px;
                                                color:#5E6658;
                                            ">
                                                Środki zostaną rozliczone
                                                zgodnie z zasadami Kasoa
                                                po zakończeniu procesu
                                                dostawy i potwierdzeniu
                                                odbioru przez kupującego.
                                            </div>







<a href="https://www.kasoa.pl" style="
                                                   display:inline-block;
                                                   background:#68764B;
                                                   color:#FFFFFF;
                                                   text-decoration:none;
                                                   font-size:15px;
                                                   font-weight:700;
                                                   padding:14px 26px;
                                                   border-radius:10px;
                                               ">
                                                Przejdź do Kasoa.pl
                                            </a>
        """.formatted(
                safeSellerName,
                safeOrderNumber,
                productsHtml.toString(),
                productsTotal.toPlainString(),
                shippingTotal.toPlainString(),
                orderTotal.toPlainString(),
                recipientAddressHtml,
                senderAddressHtml
        );

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(html)
        );
    }



    @Async
    public void sendBulkOrderActionToSellerForPersonalCollection(
            String sellerEmail,
            String sellerName,
            String orderNumber,
            List<EmailProductItemDto> products,
            String shipmentToken
    ) {

        String safeSellerName = escapeHtml(
                sellerName != null && !sellerName.isBlank()
                        ? sellerName
                        : "Sprzedawco"
        );

        String safeOrderNumber = escapeHtml(
                orderNumber != null ? orderNumber : ""
        );

        if (shipmentToken == null || shipmentToken.isBlank()) {
            throw new IllegalArgumentException(
                    "Shipment token is required for personal collection confirmation."
            );
        }

        BigDecimal productsTotal = BigDecimal.ZERO;

        StringBuilder productsHtml = new StringBuilder();

        if (products != null && !products.isEmpty()) {

            for (EmailProductItemDto item : products) {

                if (item == null) {
                    continue;
                }

                String productName = escapeHtml(
                        item.getProductName() != null
                                ? item.getProductName()
                                : "Produkt"
                );

                BigDecimal price = item.getAmount() != null
                        ? item.getAmount()
                        : BigDecimal.ZERO;

                long quantity = item.getQuantity() != null
                        ? item.getQuantity()
                        : 1L;

                BigDecimal productTotal = price.multiply(
                        BigDecimal.valueOf(quantity)
                );

                productsTotal = productsTotal.add(productTotal);

                productsHtml.append("""
                <tr>
                    <td style="
                        padding:16px 0;
                        border-bottom:1px solid #E4E7DE;
                        vertical-align:top;
                    ">
                        <div style="
                            font-size:15px;
                            line-height:22px;
                            font-weight:700;
                            color:#182016;
                        ">
                            %s
                        </div>

                        <div style="
                            margin-top:4px;
                            font-size:13px;
                            line-height:20px;
                            color:#687064;
                        ">
                            Ilość: %s
                        </div>
                    </td>

                    <td style="
                        padding:16px 0;
                        border-bottom:1px solid #E4E7DE;
                        text-align:right;
                        vertical-align:top;
                        white-space:nowrap;
                    ">
                        <div style="
                            font-size:15px;
                            line-height:22px;
                            font-weight:700;
                            color:#182016;
                        ">
                            %s PLN
                        </div>
                    </td>
                </tr>
                """.formatted(
                        productName,
                        quantity,
                        productTotal.toPlainString()
                ));
            }

        } else {

            productsHtml.append("""
            <tr>
                <td colspan="2" style="
                    padding:16px 0;
                    color:#687064;
                    font-size:14px;
                ">
                    Brak dostępnych produktów.
                </td>
            </tr>
            """);
        }

        // Personal collection has no courier shipping charge.
        BigDecimal orderTotal = productsTotal;

        String shipmentUrl = shipmentConfirmationUrl
                + "/create?token="
                + URLEncoder.encode(
                shipmentToken,
                StandardCharsets.UTF_8
        );

        String safeShipmentUrl = escapeHtml(shipmentUrl);

        String subject =
                "Kasoa.pl — Nowe zamówienie #" + safeOrderNumber;

        String html = """
<div style="
                                        font-size:14px;
                                        font-weight:700;
                                        color:#68764B;
                                        margin-bottom:8px;
                                    ">
                                        NOWE ZAMÓWIENIE
                                    </div>
<div style="
                                        font-size:25px;
                                        line-height:32px;
                                        font-weight:800;
                                        color:#182016;
                                    ">
                                        Cześć %s! 👋
                                    </div>
<div style="
                                        margin-top:12px;
                                        font-size:15px;
                                        line-height:24px;
                                        color:#5E6658;
                                    ">
                                        Otrzymałeś nowe zamówienie.
                                        Kupujący wybrał
                                        <strong>odbiór osobisty</strong>
                                        zamiast dostawy kurierskiej.
                                    </div>




<div style="
                                                    font-size:12px;
                                                    font-weight:700;
                                                    color:#68764B;
                                                    text-transform:uppercase;
                                                ">
                                                    Numer zamówienia
                                                </div>
<div style="
                                                    margin-top:4px;
                                                    font-size:18px;
                                                    font-weight:800;
                                                    color:#182016;
                                                ">
                                                    #%s
                                                </div>




<div style="
                                        margin-top:30px;
                                        font-size:18px;
                                        line-height:24px;
                                        font-weight:800;
                                        color:#182016;
                                    ">
                                        Produkty
                                    </div>

                                        %s
                                    




                                                Produkty
                                            

                                                %s PLN
                                            






                                                Razem
                                            

                                                %s PLN
                                            






<div style="
                                                    font-size:18px;
                                                    line-height:24px;
                                                    font-weight:800;
                                                    color:#182016;
                                                ">
                                                    📍 Potwierdź odbiór osobisty
                                                </div>
<div style="
                                                    margin-top:10px;
                                                    font-size:14px;
                                                    line-height:22px;
                                                    color:#5E6658;
                                                ">
                                                    Masz <strong>24 godziny</strong>
                                                    na potwierdzenie zamówienia
                                                    i podanie szczegółów odbioru.
                                                    <br/><br/>

                                                    1. Otwórz szczegóły zamówienia
                                                    za pomocą przycisku poniżej.
                                                    <br/><br/>

                                                    2. Potwierdź zamówienie,
                                                    podaj dokładny adres odbioru
                                                    oraz określ dostępne dni
                                                    i godziny, w których kupujący
                                                    może odebrać produkty.
                                                    <br/><br/>

                                                    3. Po Twoim potwierdzeniu
                                                    Kasoa wyśle kupującemu
                                                    wiadomość e-mail ze szczegółami
                                                    odbioru.
                                                    <br/><br/>

                                                    4. Kupujący będzie miał
                                                    <strong>48 godzin od momentu
                                                    Twojego potwierdzenia</strong>,
                                                    aby odebrać zamówienie.
                                                    <br/><br/>

                                                    5. Po fizycznym odebraniu
                                                    produktów kupujący potwierdzi
                                                    ich otrzymanie, klikając
                                                    bezpieczny link w wiadomości
                                                    e-mail od Kasoa.pl.
                                                </div>




<a href="%s" style="
                                                                   display:inline-block;
                                                                   background:#68764B;
                                                                   color:#FFFFFF;
                                                                   text-decoration:none;
                                                                   font-size:15px;
                                                                   font-weight:700;
                                                                   padding:14px 26px;
                                                                   border-radius:10px;
                                                               ">
                                                                Potwierdź zamówienie
                                                                i podaj szczegóły odbioru
                                                            </a>



<div style="
                                                    margin-top:16px;
                                                    font-size:12px;
                                                    line-height:18px;
                                                    color:#8A9184;
                                                    text-align:center;
                                                ">
                                                    Adres odbioru i dostępne terminy
                                                    podasz po otwarciu szczegółów
                                                    zamówienia.
                                                </div>







<div style="
                                                    font-size:16px;
                                                    font-weight:800;
                                                    color:#182016;
                                                ">
                                                    💰 Płatność
                                                </div>
<div style="
                                                    margin-top:8px;
                                                    font-size:14px;
                                                    line-height:22px;
                                                    color:#5E6658;
                                                ">
                                                    Środki zostaną rozliczone
                                                    zgodnie z zasadami Kasoa
                                                    po potwierdzeniu odbioru
                                                    produktów przez kupującego.
                                                </div>
<div style="
                                                    margin-top:20px;
                                                    padding-top:18px;
                                                    border-top:1px solid #E0E4DB;
                                                ">
<div style="
                                                        font-size:15px;
                                                        line-height:22px;
                                                        font-weight:800;
                                                        color:#182016;
                                                    ">
                                                        Dokończ konfigurację płatności
                                                    </div>
<div style="
                                                        margin-top:8px;
                                                        font-size:14px;
                                                        line-height:22px;
                                                        color:#5E6658;
                                                    ">
                                                        Aby otrzymywać wypłaty
                                                        za sprzedane produkty,
                                                        skonfiguruj konto Stripe
                                                        i zweryfikuj swoją tożsamość,
                                                        jeśli jeszcze tego nie zrobiłeś.
                                                    </div>
<div style="
                                                        margin-top:10px;
                                                        font-size:14px;
                                                        line-height:22px;
                                                        color:#5E6658;
                                                    ">
                                                        Zaloguj się na Kasoa.pl,
                                                        przejdź do
                                                        <strong>Konto &gt; Profil</strong>
                                                        i rozpocznij konfigurację Stripe.
                                                        Następnie wykonaj wymagane kroki
                                                        weryfikacji tożsamości.
                                                    </div>
</div>
        """.formatted(
                safeSellerName,
                safeOrderNumber,
                productsHtml.toString(),
                productsTotal.toPlainString(),
                orderTotal.toPlainString(),
                safeShipmentUrl
        );

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(html)
        );
    }






    /**
     *
     * @param sellerEmail
     * @param reason
     * @param orderNumber
     */

    @Async
    public void notifySellerOfBuyerReturnComplaint(
            String sellerEmail,
            String orderNumber,
            String reason
    ) {
        String safeOrderNumber = escapeHtml(
                orderNumber != null ? orderNumber : ""
        );

        String safeReason = escapeHtml(
                reason != null && !reason.isBlank()
                        ? reason
                        : "Nie podano powodu reklamacji."
        );

        String complaintUrl = escapeHtml(
                frontEndUrl + "/account/orders"
        );

        String subject =
                "Kasoa.pl — Zgłoszenie reklamacji zwrotu #" + safeOrderNumber;

        String htmlContent = """
⚠ Wymagana odpowiedź
                        
<h1 style="
                            margin: 20px 0 10px 0;
                            font-size: 26px;
                            line-height: 1.2;
                            color: #182016;
                            font-weight: 800;
                            letter-spacing: -0.6px;
                        ">
                            Otrzymano reklamację zwrotu
                        </h1>
<p style="
                            margin: 0 0 28px 0;
                            font-size: 15px;
                            line-height: 1.7;
                            color: #667066;
                        ">
                            Kupujący zgłosił reklamację dotyczącą przedmiotu
                            z zamówienia
                            <strong style="color: #68764B;">#%s</strong>.
                            Zaloguj się na swoje konto Kasoa.pl, zapoznaj się
                            ze zgłoszeniem i zdjęciami kupującego, a następnie
                            zaakceptuj reklamację lub ją odrzuć, podając uzasadnienie.
                        </p>




<div style="
                                        font-size: 10px;
                                        font-weight: 700;
                                        color: #8A9288;
                                        text-transform: uppercase;
                                        letter-spacing: 1px;
                                        margin-bottom: 8px;
                                    ">
                                        Numer zamówienia
                                    </div>
<div style="
                                        font-size: 20px;
                                        line-height: 1.4;
                                        font-weight: 800;
                                        color: #182016;
                                    ">
                                        #%s
                                    </div>




<div style="
                            margin-top: 28px;
                            font-size: 18px;
                            line-height: 24px;
                            font-weight: 800;
                            color: #182016;
                        ">
                            Powód reklamacji
                        </div>



                                    %s
                                



<div style="
                            margin-top: 30px;
                            padding-top: 24px;
                            border-top: 1px solid #E8EBE5;
                        ">
<div style="
                                font-size: 18px;
                                line-height: 24px;
                                font-weight: 800;
                                color: #182016;
                                margin-bottom: 16px;
                            ">
                                Co należy zrobić?
                            </div>
<table border="0" cellpadding="0" cellspacing="0" width="100%%">
<tr>
<td style="padding-bottom: 12px;" valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">1</div>
</td>
<td style="
                                        padding: 3px 0 12px 8px;
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #667066;
                                    " valign="top">
                                        Zaloguj się na swoje konto Kasoa.pl
                                        i otwórz szczegóły zamówienia.
                                    </td>
</tr>
<tr>
<td style="padding-bottom: 12px;" valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">2</div>
</td>
<td style="
                                        padding: 3px 0 12px 8px;
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #667066;
                                    " valign="top">
                                        Dokładnie sprawdź opis reklamacji
                                        oraz zdjęcia załączone przez kupującego.
                                    </td>
</tr>
<tr>
<td valign="top" width="32">
<div style="
                                            width: 24px;
                                            height: 24px;
                                            line-height: 24px;
                                            text-align: center;
                                            background-color: #68764B;
                                            color: #FFFFFF;
                                            border-radius: 50%%;
                                            font-size: 11px;
                                            font-weight: 700;
                                        ">3</div>
</td>
<td style="
                                        padding: 3px 0 0 8px;
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #667066;
                                    " valign="top">
                                        Zaakceptuj reklamację, jeśli uznajesz
                                        ją za zasadną. Jeśli się nie zgadzasz,
                                        odrzuć ją i podaj uzasadnienie.
                                        Sprawa zostanie wówczas przekazana
                                        do oceny administracji Kasoa.pl.
                                    </td>
</tr>
</table>
</div>




                                    ⚖️
                                

<div style="
                                        font-size: 15px;
                                        line-height: 22px;
                                        font-weight: 800;
                                        color: #795A20;
                                        margin-bottom: 8px;
                                    ">
                                        Co się stanie po odrzuceniu reklamacji?
                                    </div>
<div style="
                                        font-size: 13px;
                                        line-height: 1.7;
                                        color: #6B5A3C;
                                    ">
                                        Jeśli odrzucisz reklamację, administracja
                                        Kasoa.pl przeanalizuje zgłoszenie oraz
                                        Twoje uzasadnienie i podejmie decyzję.
                                    </div>
<div style="
                                        margin-top: 12px;
                                        font-size: 13px;
                                        line-height: 1.7;
                                        color: #6B5A3C;
                                    ">
                                        Jeśli administracja uzna, że reklamacja
                                        jest zasadna, otrzymasz informację
                                        o konieczności opłacenia przesyłki
                                        kurierskiej w celu odebrania przedmiotu
                                        od kupującego i zwrócenia go Tobie.
                                    </div>
<div style="
                                        margin-top: 12px;
                                        font-size: 13px;
                                        line-height: 1.7;
                                        color: #6B5A3C;
                                    ">
                                        Kupujący otrzyma pełny zwrot ceny zakupu
                                        przedmiotu. Koszt przesyłki zwrotnej
                                        od kupującego do Ciebie poniesiesz Ty
                                        jako sprzedający.
                                    </div>




<div style="
                            margin-top: 30px;
                            text-align: center;
                        ">
<a href="%s" style="
                                    display: inline-block;
                                    padding: 14px 28px;
                                    background-color: #68764B;
                                    color: #FFFFFF;
                                    text-decoration: none;
                                    border-radius: 9px;
                                    font-size: 13px;
                                    font-weight: 700;
                                ">
                                Otwórz swoje zamówienia →
                            </a>
</div>
        """.formatted(
                safeOrderNumber,
                safeOrderNumber,
                safeReason,
                complaintUrl
        );

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(htmlContent)
        );
    }




    /**
     *
     * @param sellerEmail
     * @param sellerName
     * @param orderNumber
     * @param products
     * @param shipmentToken
     */

    @Async
    public void sendBulkOrderActionToSeller(
            String sellerEmail,
            String sellerName,
            String orderNumber,
            List<EmailProductItemDto> products,
            String shipmentToken
    ) {
        String safeSellerName = escapeHtml(
                sellerName != null ? sellerName : "Sprzedawco"
        );
        String safeOrderNumber = escapeHtml(
                orderNumber != null ? orderNumber : ""
        );

        BigDecimal productsTotal = BigDecimal.ZERO;
        BigDecimal shippingTotal = BigDecimal.ZERO;

        StringBuilder productsHtml = new StringBuilder();

        if (products != null && !products.isEmpty()) {
            for (EmailProductItemDto item : products) {
                if (item == null) {
                    continue;
                }

                String productName = escapeHtml(
                        item.getProductName() != null
                                ? item.getProductName()
                                : "Produkt"
                );

                BigDecimal price = item.getAmount() != null
                        ? item.getAmount()
                        : BigDecimal.ZERO;

                BigDecimal shippingCost = item.getShippingCost() != null
                        ? item.getShippingCost()
                        : BigDecimal.ZERO;

                long quantity = item.getQuantity() != null
                        ? item.getQuantity()
                        : 1L;

                BigDecimal productTotal = price.multiply(
                        BigDecimal.valueOf(quantity)
                );

                productsTotal = productsTotal.add(productTotal);
                shippingTotal = shippingTotal.add(shippingCost);

                productsHtml.append("""
                    <tr>
                        <td style="
                            padding:16px 0;
                            border-bottom:1px solid #E4E7DE;
                            vertical-align:top;
                        ">
                            <div style="
                                font-size:15px;
                                line-height:22px;
                                font-weight:700;
                                color:#182016;
                            ">
                                %s
                            </div>
                            <div style="
                                margin-top:4px;
                                font-size:13px;
                                line-height:20px;
                                color:#687064;
                            ">
                                Ilość: %s
                            </div>
                        </td>
                        <td style="
                            padding:16px 0;
                            border-bottom:1px solid #E4E7DE;
                            text-align:right;
                            vertical-align:top;
                            white-space:nowrap;
                        ">
                            <div style="
                                font-size:15px;
                                line-height:22px;
                                font-weight:700;
                                color:#182016;
                            ">
                                %s PLN
                            </div>
                        </td>
                    </tr>
                    """.formatted(
                        productName,
                        quantity,
                        productTotal.toPlainString()
                ));
            }
        } else {
            productsHtml.append("""
                <tr>
                    <td colspan="2" style="
                        padding:16px 0;
                        color:#687064;
                        font-size:14px;
                    ">
                        Brak dostępnych produktów.
                    </td>
                </tr>
                """);
        }

        BigDecimal orderTotal = productsTotal.add(shippingTotal);

        String shipmentUrl = shipmentConfirmationUrl
                + "/create?token="
                + URLEncoder.encode(
                shipmentToken,
                StandardCharsets.UTF_8
        );

        String subject =
                "Kasoa.pl — Nowe zamówienie #" + safeOrderNumber;

        String html = """
<div style="
                                            font-size:14px;
                                            font-weight:700;
                                            color:#68764B;
                                            margin-bottom:8px;
                                        ">
                                            NOWE ZAMÓWIENIE
                                        </div>
<div style="
                                            font-size:25px;
                                            line-height:32px;
                                            font-weight:800;
                                            color:#182016;
                                        ">
                                            Cześć %s! 👋
                                        </div>
<div style="
                                            margin-top:12px;
                                            font-size:15px;
                                            line-height:24px;
                                            color:#5E6658;
                                        ">
                                            Otrzymałeś nowe zamówienie.
                                            Przygotuj produkty do wysyłki,
                                            a następnie utwórz przesyłkę InPost
                                            za pomocą przycisku poniżej.
                                        </div>




<div style="
                                                        font-size:12px;
                                                        font-weight:700;
                                                        color:#68764B;
                                                        text-transform:uppercase;
                                                    ">
                                                        Numer zamówienia
                                                    </div>
<div style="
                                                        margin-top:4px;
                                                        font-size:18px;
                                                        font-weight:800;
                                                        color:#182016;
                                                    ">
                                                        #%s
                                                    </div>




<div style="
                                            margin-top:30px;
                                            font-size:18px;
                                            line-height:24px;
                                            font-weight:800;
                                            color:#182016;
                                        ">
                                            Produkty
                                        </div>

                                            %s
                                        




                                                    Produkty
                                                

                                                    %s PLN
                                                



                                                    Dostawa
                                                

                                                    %s PLN
                                                






                                                    Razem
                                                

                                                    %s PLN
                                                






<div style="
                                                        font-size:18px;
                                                        line-height:24px;
                                                        font-weight:800;
                                                        color:#182016;
                                                    ">
                                                        📦 Przygotuj wysyłkę
                                                    </div>
<div style="
                                                        margin-top:10px;
                                                        font-size:14px;
                                                        line-height:22px;
                                                        color:#5E6658;
                                                    ">
                                                        Przygotuj bezpiecznie zapakowany
                                                        produkt. Następnie utwórz przesyłkę
                                                        za pomocą przycisku poniżej.
                                                    </div>



<a href="%s" style="
                                                                       display:inline-block;
                                                                       background:#68764B;
                                                                       color:#FFFFFF;
                                                                       text-decoration:none;
                                                                       font-size:15px;
                                                                       font-weight:700;
                                                                       padding:14px 26px;
                                                                       border-radius:10px;
                                                                   ">
                                                                Utwórz zlecenie wysyłki
                                                                </a>



<div style="
                                                        margin-top:16px;
                                                        font-size:12px;
                                                        line-height:18px;
                                                        color:#8A9184;
                                                        text-align:center;
                                                    ">
                                                        Dane odbiorcy, nadawcy oraz
                                                        szczegóły przesyłki zostaną
                                                        automatycznie uzupełnione przez Kasoa.
                                                    </div>







<div style="
                                                        font-size:16px;
                                                        font-weight:800;
                                                        color:#182016;
                                                    ">
                                                        💰 Płatność
                                                    </div>
<div style="
                                                        margin-top:8px;
                                                        font-size:14px;
                                                        line-height:22px;
                                                        color:#5E6658;
                                                    ">
                                                        Środki zostaną rozliczone
                                                        zgodnie z zasadami Kasoa
                                                        po zakończeniu procesu dostawy
                                                        i potwierdzeniu odbioru
                                                        przez kupującego.
                                                    </div>
<div style="
                                                        margin-top:20px;
                                                        padding-top:18px;
                                                        border-top:1px solid #E0E4DB;
                                                    ">
<div style="
                                                            font-size:15px;
                                                            line-height:22px;
                                                            font-weight:800;
                                                            color:#182016;
                                                        ">
                                                            Dokończ konfigurację płatności
                                                        </div>
<div style="
                                                            margin-top:8px;
                                                            font-size:14px;
                                                            line-height:22px;
                                                            color:#5E6658;
                                                        ">
                                                            Aby otrzymywać wypłaty za
                                                            sprzedane produkty, skonfiguruj
                                                            konto Stripe i zweryfikuj swoją
                                                            tożsamość, jeśli jeszcze tego
                                                            nie zrobiłeś.
                                                        </div>
<div style="
                                                            margin-top:10px;
                                                            font-size:14px;
                                                            line-height:22px;
                                                            color:#5E6658;
                                                        ">
                                                            Zaloguj się na Kasoa.pl,
                                                            przejdź do
                                                            <strong>Konto &gt; Profil</strong>
                                                            i rozpocznij konfigurację Stripe.
                                                            Następnie wykonaj wymagane kroki
                                                            weryfikacji tożsamości.
                                                        </div>
</div>
        """.formatted(
                safeSellerName,
                safeOrderNumber,
                productsHtml.toString(),
                productsTotal.toPlainString(),
                shippingTotal.toPlainString(),
                orderTotal.toPlainString(),
                shipmentUrl
        );

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(html)
        );
    }

    /**
     * Notify buyer of seller response
     */

    @Async
    public void sendSellerResponseToBuyerComplaint(
            String email,
            String orderNumber,
            String decision,
            String reason
    ) {
        String subject = "Aktualizacja dotycząca zwrotu lub reklamacji - zamówienie nr "
                + orderNumber;

        String safeOrderNumber = escapeHtml(
                orderNumber == null || orderNumber.isBlank() ? "-" : orderNumber
        );

        String safeDecision = escapeHtml(
                decision == null || decision.isBlank()
                        ? "Odrzucono wniosek"
                        : decision
        );

        String safeReason = escapeHtml(
                reason == null || reason.isBlank()
                        ? "Sprzedający nie podał uzasadnienia."
                        : reason
        );

        String html = """
<h1 style="margin:0 0 18px;font-size:23px;
                             line-height:1.35;font-weight:700;color:#182016;">
                    Aktualizacja dotycząca zwrotu lub reklamacji
                  </h1>
<p style="margin:0 0 22px;font-size:15px;
                            line-height:1.7;color:#39433A;">
                    Sprzedający rozpatrzył Twój wniosek dotyczący zwrotu
                    lub reklamacji i go odrzucił. Poniżej znajdziesz
                    odpowiedź sprzedającego.
                  </p>




<p style="margin:0 0 6px;font-size:12px;
                                  line-height:1.5;font-weight:600;
                                  color:#68764B;text-transform:uppercase;
                                  letter-spacing:0.5px;">
                          Numer zamówienia
                        </p>
<p style="margin:0 0 20px;font-size:16px;
                                  line-height:1.5;font-weight:700;
                                  color:#182016;">
                          #%s
                        </p>
<p style="margin:0 0 6px;font-size:12px;
                                  line-height:1.5;font-weight:600;
                                  color:#68764B;text-transform:uppercase;
                                  letter-spacing:0.5px;">
                          Decyzja sprzedającego
                        </p>
<p style="margin:0 0 20px;font-size:15px;
                                  line-height:1.6;font-weight:700;
                                  color:#9B2C2C;">
                          %s
                        </p>
<p style="margin:0 0 6px;font-size:12px;
                                  line-height:1.5;font-weight:600;
                                  color:#68764B;text-transform:uppercase;
                                  letter-spacing:0.5px;">
                          Uzasadnienie sprzedającego
                        </p>
<p style="margin:0;font-size:14px;
                                  line-height:1.7;color:#39433A;
                                  white-space:pre-line;overflow-wrap:anywhere;">
                          %s
                        </p>







<h2 style="margin:0 0 12px;font-size:17px;
                                   line-height:1.4;font-weight:700;
                                   color:#1E3A3A;">
                          Sprawa jest w trakcie weryfikacji
                        </h2>
<p style="margin:0 0 12px;font-size:14px;
                                  line-height:1.7;color:#39433A;">
                          Twoja sprawa została przekazana administratorowi
                          Kasoa.pl do dalszego rozpatrzenia.
                        </p>
<p style="margin:0 0 12px;font-size:14px;
                                  line-height:1.7;color:#39433A;">
                          Administrator przeanalizuje uzasadnienie
                          sprzedającego, przesłane przez Ciebie zdjęcia
                          dowodowe oraz pierwotne ogłoszenie produktu
                          przed podjęciem ostatecznej decyzji.
                        </p>
<p style="margin:0;font-size:14px;
                                  line-height:1.7;color:#39433A;">
                          Powiadomimy Cię po zakończeniu weryfikacji.
                          Odrzucenie wniosku przez sprzedającego nie oznacza,
                          że Kasoa.pl podjęło już ostateczną decyzję
                          w Twojej sprawie.
                        </p>



<p style="margin:25px 0 0;font-size:14px;
                            line-height:1.7;color:#39433A;">
                    Dziękujemy za cierpliwość podczas rozpatrywania sprawy.
                  </p>
<p style="margin:22px 0 0;font-size:14px;
                            line-height:1.7;color:#39433A;">
                    Pozdrawiamy,<br/>
<strong style="color:#182016;">Zespół Kasoa.pl</strong>
</p>
        """.formatted(
                safeOrderNumber,
                safeDecision,
                safeReason
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }



    /**
     * Notify buyer of Admin response after seller rejections
     */
    @Async
    public void sendAdminResponseToBuyerComplaint(
            String email,
            String orderNumber,
            String decision,
            String reason
    ) {
        String normalizedDecision = decision == null
                ? ""
                : decision.trim().toUpperCase(Locale.ROOT);

        boolean approved = normalizedDecision.equals("APPROVED")
                || normalizedDecision.equals("APPROVE");

        String subject = approved
                ? "Zwrot lub reklamacja zostały zatwierdzone - zamówienie nr " + orderNumber
                : "Decyzja dotycząca zwrotu lub reklamacji - zamówienie nr " + orderNumber;

        String safeOrderNumber = escapeHtml(orderNumber);
        String safeDecision = approved ? "Zatwierdzono (Approved)" : "Odrzucono (Rejected)";

        String safeReason = escapeHtml(
                reason == null || reason.isBlank()
                        ? "Nie podano dodatkowego uzasadnienia."
                        : reason
        );

        String decisionMessage = approved
                ? """
              Administrator Kasoa.pl rozpatrzył Twój wniosek dotyczący zwrotu
              lub reklamacji i go zatwierdził.
              """
                : """
              Administrator Kasoa.pl rozpatrzył Twój wniosek dotyczący zwrotu
              lub reklamacji i go odrzucił.
              """;

        String nextSteps = approved
                ? """
              <h3 style="margin:24px 0 12px;font-size:18px;color:#1E3A3A;">
                Co dalej?
              </h3>

              <p style="margin:0 0 12px;font-size:15px;line-height:1.6;">
                Przygotuj produkt do zwrotu i zapakuj go w sposób zapewniający
                odpowiednią ochronę podczas transportu.
              </p>

              <p style="margin:0 0 12px;font-size:15px;line-height:1.6;">
                Kurier odbierze przesyłkę spod tego samego adresu, na który
                produkt został pierwotnie dostarczony. Koszt odbioru przesyłki
                przez kuriera pokrywa sprzedający.
              </p>

              <p style="margin:0;font-size:15px;line-height:1.6;">
                Poczekaj na dalsze instrukcje od Kasoa.pl dotyczące odbioru.
                Nie wysyłaj produktu samodzielnie, chyba że otrzymasz takie
                polecenie.
              </p>
              """
                : """
              <p style="margin:20px 0 0;font-size:15px;line-height:1.6;">
                Jeśli masz pytania dotyczące tej decyzji, skontaktuj się
                z obsługą Kasoa.pl, podając numer zamówienia.
              </p>
              """;

        String html = """
<h2 style="margin:0 0 16px;font-size:22px;color:#0D1313;">
                        Decyzja dotycząca zwrotu lub reklamacji
                      </h2>
<p style="margin:0 0 16px;font-size:15px;line-height:1.6;">
                        %s
                      </p>



<p style="margin:0 0 8px;font-size:13px;color:#5E6C6A;">
                              Numer zamówienia
                            </p>
<p style="margin:0 0 16px;font-size:16px;font-weight:700;">
                              #%s
                            </p>
<p style="margin:0 0 8px;font-size:13px;color:#5E6C6A;">
                              Decyzja administratora
                            </p>
<p style="margin:0 0 16px;font-size:15px;font-weight:700;
                                      color:%s;">
                              %s
                            </p>
<p style="margin:0 0 8px;font-size:13px;color:#5E6C6A;">
                              Uzasadnienie decyzji
                            </p>
<p style="margin:0;font-size:15px;line-height:1.6;
                                      white-space:pre-line;overflow-wrap:anywhere;">
                              %s
                            </p>




                      %s

                      <p style="margin:24px 0 0;font-size:15px;line-height:1.6;">
                        Pozdrawiamy,<br/>
<strong>Zespół Kasoa.pl</strong>
</p>




                      Kasoa.pl — prostszy sposób na kupowanie i sprzedawanie.<br/>
                      To automatyczne powiadomienie dotyczące Twojego zamówienia.
        """.formatted(
                escapeHtml(decisionMessage),
                safeOrderNumber,
                approved ? "#2A6B4E" : "#9B2C2C",
                safeDecision,
                safeReason,
                nextSteps
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }




    /**
     *
     * @param sellerEmail
     * @param orderNumber
     * @param complaintId
     * @param stripePaymentUrl
     */
    @Async
    public void sendReturnShippingPaymentRequest(
            String sellerEmail,
            String orderNumber,
            String complaintId,
            String stripePaymentUrl
    ) {
        String safeOrderNumber = escapeHtml(
                orderNumber != null ? orderNumber : ""
        );

        String safeComplaintId = escapeHtml(
                complaintId != null ? complaintId : ""
        );

        String safePaymentUrl = escapeHtml(
                stripePaymentUrl != null ? stripePaymentUrl : ""
        );

        String subject = "Kasoa.pl — Opłać przesyłkę zwrotną dla zamówienia #"
                + safeOrderNumber;

        String htmlContent = """
Wymagana płatność
                        
<h1 style="
                            margin: 20px 0 10px;
                            font-size: 26px;
                            line-height: 1.2;
                            color: #182016;
                            font-weight: 800;
                            letter-spacing: -0.6px;
                        ">
                            Opłać przesyłkę zwrotną
                        </h1>
<p style="
                            margin: 0 0 24px;
                            font-size: 15px;
                            line-height: 1.7;
                            color: #667066;
                        ">
                            Zwrot przedmiotu z zamówienia
                            <strong style="color: #68764B;">#%s</strong>
                            został zaakceptowany. Aby zorganizować odbiór
                            przedmiotu od kupującego i jego dostarczenie
                            do Ciebie, opłać przesyłkę zwrotną za pomocą
                            bezpiecznej strony płatności Stripe.
                        </p>




<div style="
                                        font-size: 10px;
                                        font-weight: 700;
                                        color: #8A9288;
                                        text-transform: uppercase;
                                        letter-spacing: 1px;
                                        margin-bottom: 8px;
                                    ">
                                        Numer zamówienia
                                    </div>
<div style="
                                        font-size: 20px;
                                        line-height: 1.4;
                                        font-weight: 800;
                                        color: #182016;
                                    ">
                                        #%s
                                    </div>
<div style="
                                        margin-top: 16px;
                                        font-size: 10px;
                                        font-weight: 700;
                                        color: #8A9288;
                                        text-transform: uppercase;
                                        letter-spacing: 1px;
                                        margin-bottom: 8px;
                                    ">
                                        Numer zgłoszenia
                                    </div>
<div style="
                                        font-size: 14px;
                                        font-weight: 700;
                                        color: #182016;
                                    ">
                                        %s
                                    </div>







<div style="
                                        font-size: 15px;
                                        line-height: 22px;
                                        font-weight: 800;
                                        color: #182016;
                                        margin-bottom: 8px;
                                    ">
                                        Co należy zrobić?
                                    </div>
<div style="
                                        font-size: 13px;
                                        line-height: 1.7;
                                        color: #5E6658;
                                    ">
                                        1. Kliknij przycisk poniżej i opłać
                                        przesyłkę zwrotną.
                                        <br/><br/>
                                        2. Po potwierdzeniu płatności
                                        Kasoa.pl zorganizuje odbiór przedmiotu
                                        od kupującego i jego zwrot do Ciebie.
                                    </div>




<p style="
                            margin: 24px 0 0;
                            font-size: 13px;
                            line-height: 1.7;
                            color: #667066;
                        ">
                            Zwrot środków kupującemu zostanie obsłużony
                            zgodnie z zasadami obowiązującymi dla tego zwrotu.
                            Koszt przesyłki zwrotnej ponosisz jako sprzedający,
                            zgodnie z ustaleniami dotyczącymi tej sprawy.
                        </p>

<div style="
                            margin-top: 30px;
                            text-align: center;
                        ">
<a href="%s" style="
                                   display: inline-block;
                                   padding: 14px 28px;
                                   background-color: #68764B;
                                   color: #FFFFFF;
                                   text-decoration: none;
                                   border-radius: 9px;
                                   font-size: 13px;
                                   font-weight: 700;
                               ">
                                Opłać przesyłkę zwrotną →
                            </a>
</div>
<p style="
                            margin: 20px 0 0;
                            font-size: 11px;
                            line-height: 1.6;
                            color: #8A9288;
                            text-align: center;
                        ">
                            Jeśli przycisk nie działa, skopiuj poniższy link
                            i otwórz go w przeglądarce:
                        </p>
<p style="
                            margin: 8px 0 0;
                            font-size: 11px;
                            line-height: 1.6;
                            text-align: center;
                            word-break: break-all;
                        ">
<a href="%s" style="
                                color: #68764B;
                                text-decoration: underline;
                            ">%s</a>
</p>
        """.formatted(
                safeOrderNumber,
                safeOrderNumber,
                safeComplaintId,
                safePaymentUrl,
                safePaymentUrl,
                safePaymentUrl
        );

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(htmlContent)
        );
    }



    /**
     * Sends a dynamic shipment tracking notification to the Buyer for the Kasoa MVP.
     * Automatically adapts the subject and message header box to match any lifecycle status.
     */
    @Async
    private record ShipmentStatusContent(
            String label,
            String subject,
            String description
    ) {}





    public void sendShipmentStatusToBuyerx(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName,
            String shipmentStatus,
            String trackingNumber,
            String shippingMethod,
            String receiptConfirmationToken
    ) {

        String safeBuyerName = escapeHtml(buyerName);
        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProductName = escapeHtml(productName);
        String safeShipmentStatus = escapeHtml(formatShipmentStatus(shipmentStatus));
        String safeTrackingNumber = escapeHtml(trackingNumber);
        String safeShippingMethod = escapeHtml(shippingMethod);

        String subject;

        switch (shipmentStatus.toUpperCase()) {

            case "SHIPPED" ->
                    subject = "Twoje zamówienie #" + orderNumber
                            + " zostało wysłane 📦";

            case "DELIVERED" ->
                    subject = "Twoje zamówienie #" + orderNumber
                            + " zostało dostarczone ✓";

            case "RECEIVED" ->
                    subject = "Odbiór zamówienia #" + orderNumber
                            + " został potwierdzony ✓";

            default ->
                    subject = "Aktualizacja zamówienia #" + orderNumber;
        }


        /*
         * ============================================================
         * RECEIPT CONFIRMATION
         * ============================================================
         *
         * The confirmation button is displayed for:
         *
         * SHIPPED
         * DELIVERED
         *
         * The URL does NOT directly confirm the order.
         * It opens a confirmation page where the buyer must explicitly
         * confirm receipt.
         */

        String receiptConfirmationHtml = "";

        boolean confirmationStatus =
                "SHIPPED".equalsIgnoreCase(shipmentStatus)
                        || "DELIVERED".equalsIgnoreCase(shipmentStatus);

        boolean hasConfirmationToken =
                receiptConfirmationToken != null
                        && !receiptConfirmationToken.isBlank();

        if (confirmationStatus && hasConfirmationToken) {

            String receiptConfirmationUrl =
                    deliveryConfirmationUrl
                            + "/confirm-receipt?token="
                            + URLEncoder.encode(
                            receiptConfirmationToken,
                            StandardCharsets.UTF_8
                    );

            String confirmationTitle;

            if ("DELIVERED".equalsIgnoreCase(shipmentStatus)) {
                confirmationTitle = "Przesyłka została dostarczona";
            } else {
                confirmationTitle = "Otrzymałeś przesyłkę?";
            }

            receiptConfirmationHtml = """
            <table width="100%%"
                   cellpadding="0"
                   cellspacing="0"
                   border="0"
                   style="
                       width:100%%;
                       background:#EEF1E8;
                       border:1px solid #DDE4D1;
                       border-radius:14px;
                       margin:0 0 24px 0;
                   ">

                <tr>
                    <td style="
                        padding:26px 24px;
                        text-align:center;
                    ">

                        <div style="
                            font-size:30px;
                            line-height:36px;
                            margin-bottom:10px;
                        ">
                            📦
                        </div>

                        <div style="
                            font-size:18px;
                            line-height:26px;
                            font-weight:700;
                            color:#182016;
                            margin-bottom:10px;
                        ">
                            %s
                        </div>

                        <div style="
                            font-size:14px;
                            line-height:22px;
                            color:#596052;
                            margin-bottom:20px;
                        ">
                            Otrzymałeś przesyłkę?
                            Sprawdź przedmiot i jeśli wszystko
                            jest zgodne z zamówieniem, potwierdź
                            odbiór.
                        </div>

                        <a href="%s"
                           style="
                               display:inline-block;
                               background:#68764B;
                               color:#FFFFFF;
                               text-decoration:none;
                               font-size:15px;
                               line-height:20px;
                               font-weight:700;
                               padding:14px 24px;
                               border-radius:10px;
                           ">
                            ✓ Potwierdź odbiór przedmiotu
                        </a>

                        <div style="
                            font-size:11px;
                            line-height:18px;
                            color:#7A8172;
                            margin-top:16px;
                        ">
                            Potwierdź odbiór dopiero po otrzymaniu
                            i sprawdzeniu przesyłki.
                        </div>

                    </td>
                </tr>

            </table>
            """.formatted(
                    escapeHtml(confirmationTitle),
                    escapeHtml(receiptConfirmationUrl)
            );
        }


        /*
         * ============================================================
         * TRACKING INFORMATION
         * ============================================================
         */

        String trackingHtml = "";

        if (trackingNumber != null && !trackingNumber.isBlank()) {

            trackingHtml = """
            <tr>
                <td style="
                    padding:0 0 18px 0;
                ">

                    <div style="
                        font-size:12px;
                        line-height:18px;
                        color:#7A8172;
                        margin-bottom:4px;
                    ">
                        NUMER PRZESYŁKI
                    </div>

                    <div style="
                        font-size:15px;
                        line-height:22px;
                        font-weight:600;
                        color:#182016;
                    ">
                        %s
                    </div>

                </td>
            </tr>
            """.formatted(
                    safeTrackingNumber
            );
        }


        /*
         * ============================================================
         * SHIPPING METHOD
         * ============================================================
         */

        String shippingMethodHtml = "";

        if (shippingMethod != null && !shippingMethod.isBlank()) {

            shippingMethodHtml = """
            <tr>
                <td style="
                    padding:0 0 18px 0;
                ">

                    <div style="
                        font-size:12px;
                        line-height:18px;
                        color:#7A8172;
                        margin-bottom:4px;
                    ">
                        SPOSÓB DOSTAWY
                    </div>

                    <div style="
                        font-size:15px;
                        line-height:22px;
                        font-weight:600;
                        color:#182016;
                    ">
                        %s
                    </div>

                </td>
            </tr>
            """.formatted(
                    safeShippingMethod
            );
        }


        /*
         * ============================================================
         * NEXT STEP
         * ============================================================
         */

        String nextStepText = getNextStepText(shipmentStatus);


        /*
         * ============================================================
         * MAIN EMAIL
         * ============================================================
         */

        String html = """
<div style="
                                    font-size:24px;
                                    line-height:32px;
                                    font-weight:700;
                                    color:#182016;
                                    margin-bottom:8px;
                                ">
                                    Cześć %s! 👋
                                </div>
<div style="
                                    font-size:15px;
                                    line-height:24px;
                                    color:#596052;
                                    margin-bottom:24px;
                                ">
                                    Mamy aktualizację dotyczącą Twojego
                                    zamówienia.
                                </div>



<table border="0" cellpadding="0" cellspacing="0" style="
                                           margin:0 0 24px 0;
                                       ">
<tr>
<td style="
                                            background:#EEF1E8;
                                            border-radius:20px;
                                            padding:8px 14px;
                                            font-size:13px;
                                            line-height:18px;
                                            font-weight:700;
                                            color:#68764B;
                                        ">
                                            %s
                                        </td>
</tr>
</table>






<div style="
                                                font-size:12px;
                                                line-height:18px;
                                                color:#7A8172;
                                                margin-bottom:5px;
                                            ">
                                                ZAMÓWIENIE
                                            </div>
<div style="
                                                font-size:20px;
                                                line-height:27px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:20px;
                                            ">
                                                #%s
                                            </div>
<div style="
                                                font-size:12px;
                                                line-height:18px;
                                                color:#7A8172;
                                                margin-bottom:5px;
                                            ">
                                                PRZEDMIOT
                                            </div>
<div style="
                                                font-size:16px;
                                                line-height:23px;
                                                font-weight:600;
                                                color:#182016;
                                            ">
                                                %s
                                            </div>









<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:20px;
                                            ">
                                                📦 Informacje o przesyłce
                                            </div>


                                                %s
                                                %s

                                            







                                %s


                                





<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:10px;
                                            ">
                                                🔒 Bezpieczna transakcja
                                            </div>
<div style="
                                                font-size:14px;
                                                line-height:22px;
                                                color:#596052;
                                            ">
                                                Płatność jest zabezpieczona
                                                w systemie kasoa.pl.
                                                Środki zostaną rozliczone
                                                zgodnie z zasadami transakcji
                                                po zakończeniu procesu.
                                            </div>









<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:12px;
                                            ">
                                                Co dalej?
                                            </div>
<div style="
                                                font-size:14px;
                                                line-height:23px;
                                                color:#4F5849;
                                            ">
                                                %s
                                            </div>









<a href="https://www.kasoa.pl" style="
                                                   display:inline-block;
                                                   background:#68764B;
                                                   color:#FFFFFF;
                                                   text-decoration:none;
                                                   font-size:15px;
                                                   line-height:20px;
                                                   font-weight:700;
                                                   padding:14px 28px;
                                                   border-radius:10px;
                                               ">
                                                Przejdź do kasoa.pl →
                                            </a>
        """.formatted(
                safeBuyerName,
                safeShipmentStatus,
                safeOrderNumber,
                safeProductName,
                trackingHtml,
                shippingMethodHtml,
                receiptConfirmationHtml,
                nextStepText
        );


        sendHtmlEmail(
                buyerEmail,
                subject,
                buildEmailLayout(html)
        );
    }





    @Async
    public void checkForAvailabilityWithSeller(
            String email,
            String product,
            String baseURL,
            String token
    ) {
        String subject = "Kupujący interesują się Twoim produktem – potwierdź dostępność";

        String safeProduct = escapeHtml(product);

        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String availableUrl = baseURL + "/confirm?token=" + encodedToken;
        String soldUrl = baseURL + "/sold?token=" + encodedToken;

        String html = """
<h1 style="
                                        margin: 0 0 18px 0;
                                        font-size: 22px;
                                        line-height: 29px;
                                        font-weight: bold;
                                        color: #182016;
                                        text-align: left;
                                    ">
                                        Cześć!
                                    </h1>
<p style="
                                        margin: 0 0 16px 0;
                                        font-size: 15px;
                                        line-height: 24px;
                                        color: #394235;
                                        text-align: left;
                                    ">
                                        Zauważyliśmy zainteresowanie Twoim produktem.
                                        Zanim kupujący złoży zamówienie, chcemy upewnić się,
                                        że oferta jest nadal aktualna.
                                    </p>




<div style="
                                                    margin-bottom: 8px;
                                                    color: #68764B;
                                                    font-size: 11px;
                                                    line-height: 16px;
                                                    font-weight: bold;
                                                    letter-spacing: 1px;
                                                ">
                                                    TWOJA OFERTA
                                                </div>
<div style="
                                                    color: #182016;
                                                    font-size: 17px;
                                                    line-height: 24px;
                                                    font-weight: bold;
                                                    word-break: break-word;
                                                ">
                                                    %s
                                                </div>



<p style="
                                        margin: 0 0 16px 0;
                                        font-size: 15px;
                                        line-height: 24px;
                                        color: #394235;
                                        text-align: left;
                                    ">
                                        Wybierz jedną z poniższych opcji:
                                    </p>
<ul style="
                                        margin: 0 0 22px 0;
                                        padding-left: 21px;
                                        color: #394235;
                                        font-size: 14px;
                                        line-height: 23px;
                                    ">
<li style="padding-bottom: 8px;">
                                            Potwierdź, jeśli produkt jest nadal dostępny.
                                        </li>
<li>
                                            Zaznacz, że został sprzedany poza Kasoa lub nie
                                            jest już dostępny, jeśli nie możesz go wysłać.
                                        </li>
</ul>




<div style="
                                                    margin-bottom: 6px;
                                                    color: #70551C;
                                                    font-size: 14px;
                                                    line-height: 20px;
                                                    font-weight: bold;
                                                ">
                                                    Ważne – uniknij anulowania zamówienia
                                                </div>
<p style="
                                                    margin: 0;
                                                    color: #70551C;
                                                    font-size: 13px;
                                                    line-height: 21px;
                                                ">
                                                    Aktualizowanie dostępności pomaga uniknąć
                                                    zamówień na produkty, których nie możesz
                                                    już sprzedać. Anulowanie opłaconego
                                                    zamówienia może wiązać się z dodatkowymi
                                                    konsekwencjami.
                                                </p>







<h2 style="
                                                    margin: 0 0 17px 0;
                                                    color: #182016;
                                                    font-size: 17px;
                                                    line-height: 24px;
                                                    text-align: left;
                                                ">
                                                    Potwierdź dostępność produktu
                                                </h2>



<a href="%s" style="
                                                                   display: block;
                                                                   padding: 15px 12px;
                                                                   color: #FFFFFF;
                                                                   font-size: 14px;
                                                                   line-height: 20px;
                                                                   font-weight: bold;
                                                                   text-decoration: none;
                                                                   text-align: center;
                                                                   border-radius: 7px;
                                                               ">
                                                                ✓ Produkt jest nadal dostępny
                                                            </a>




                                                             
                                                        




<a href="%s" style="
                                                                   display: block;
                                                                   padding: 14px 12px;
                                                                   color: #A8423B;
                                                                   font-size: 14px;
                                                                   line-height: 20px;
                                                                   font-weight: bold;
                                                                   text-decoration: none;
                                                                   text-align: center;
                                                                   border-radius: 7px;
                                                               ">
                                                                ✕ Produkt został sprzedany
                                                            </a>



<p style="
                                                    margin: 16px 0 0 0;
                                                    color: #59634F;
                                                    font-size: 12px;
                                                    line-height: 19px;
                                                    text-align: left;
                                                ">
                                                    Możesz zmienić swoją decyzję, korzystając
                                                    z tego samego linku, dopóki pozostaje
                                                    aktywny.
                                                </p>




<p style="
                                        margin: 22px 0 12px 0;
                                        color: #394235;
                                        font-size: 14px;
                                        line-height: 23px;
                                        text-align: left;
                                    ">
<strong>Co się stanie, jeśli nie odpowiesz?</strong>
</p>
<p style="
                                        margin: 0 0 16px 0;
                                        color: #394235;
                                        font-size: 14px;
                                        line-height: 23px;
                                        text-align: left;
                                    ">
                                        Jeśli nie potwierdzisz dostępności w ciągu 24 godzin,
                                        Twoja oferta zostanie przeniesiona do wersji roboczych
                                        i przestanie być widoczna dla kupujących.
                                    </p>
<p style="
                                        margin: 0 0 24px 0;
                                        color: #394235;
                                        font-size: 14px;
                                        line-height: 23px;
                                        text-align: left;
                                    ">
                                        W każdej chwili możesz ponownie opublikować produkt
                                        w sekcji <strong>Moje konto → Moje produkty</strong>,
                                        zmieniając jego status z
                                        <strong>Wersja robocza</strong> na
                                        <strong>Opublikowane</strong>.
                                    </p>




<a href="%s" style="
                                                       color: #68764B;
                                                       font-size: 14px;
                                                       line-height: 22px;
                                                       font-weight: bold;
                                                       text-decoration: none;
                                                   ">
                                                    Przejdź do kasoa.pl →
                                                </a>
        """.formatted(
                safeProduct,
                escapeHtml(availableUrl),
                escapeHtml(soldUrl),
                escapeHtml(baseURL)
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }


    /**
     * notify selelr of buyer enquiry
     * @param email
     * @param product
     * @param enquiry
     * @param token
     */
    @Async
    public void notifySellerToUpdateListing(
            String email,
            String product,
            String enquiry,
            String token
    ) {
        String subject = "Kupujący ma pytanie dotyczące Twojego produktu";

        String safeProduct = escapeHtml(product);
        String safeEnquiry = escapeHtml(enquiry);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);

        String baseURL = frontEndUrl.replaceAll("/+$", "");

        String updateUrl = baseURL + "/update-listing?token=" + encodedToken;
        String soldUrl = baseURL + "/sold?token=" + encodedToken;

        String html = """
<h2 style="font-size:22px;margin-top:0;">
                                        Kupujący ma pytanie dotyczące Twojego produktu
                                    </h2>
<p style="font-size:15px;line-height:1.6;">
                                        Kupujący potrzebuje dodatkowych informacji dotyczących
                                        wystawionego przez Ciebie produktu.
                                    </p>

<strong>%s</strong>


<strong>Pytanie kupującego:</strong>
<p style="margin-bottom:0;line-height:1.6;">%s</p>

<p style="font-size:15px;line-height:1.6;">
                                        Kliknij poniższy przycisk, aby uzupełnić informacje
                                        w ogłoszeniu. Dodane informacje będą widoczne dla
                                        wszystkich kupujących.
                                    </p>

<a href="%s" style="display:inline-block;background:#68764B;color:#ffffff;text-decoration:none;padding:14px 28px;border-radius:6px;font-weight:bold;">
                                            Uzupełnij informacje
                                        </a>

<p style="font-size:14px;line-height:1.6;">
                                        Po zaktualizowaniu ogłoszenia kupujący, którzy
                                        poprosili o dodatkowe informacje, otrzymają powiadomienie.
                                    </p>
<hr style="border:none;border-top:1px solid #E0E7E6;margin:28px 0;"/>
<h3 style="font-size:17px;">
                                        Produkt został już sprzedany?
                                    </h3>
<p style="font-size:14px;line-height:1.6;">
                                        Jeśli produkt nie jest już dostępny, możesz oznaczyć
                                        go jako sprzedany, korzystając z poniższego linku.
                                    </p>

<a href="%s" style="display:inline-block;background:#1E3A3A;color:#ffffff;text-decoration:none;padding:13px 25px;border-radius:6px;font-weight:bold;">
                                            Oznacz jako sprzedany
                                        </a>

<p style="font-size:12px;color:#68736B;line-height:1.5;">
                                        Linki są przeznaczone wyłącznie dla Ciebie.
                                        Nie udostępniaj ich innym osobom.
                                    </p>




<p style="margin:0;font-size:13px;">
                                        Dziękujemy za korzystanie z Kasoa!
                                    </p>
        """.formatted(
                safeProduct,
                safeEnquiry,
                escapeHtml(updateUrl),
                escapeHtml(soldUrl),
                escapeHtml(baseURL)
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }


    /**
     * Notify buyer after seller update
     * @param email
     * @param product
     * @param productUrl
     */
    @Async
    public void notifyBuyerAfterSellerUpdate(
            String email,
            String product,
            String productUrl
    ) {
        String subject = "Sprzedający uzupełnił informacje o produkcie";

        String safeProduct = escapeHtml(product);
        String fullProductUrl = frontEndUrl.replaceAll("/+$", "")
                + "/" + productUrl.replaceAll("^/+", "");

        String html = """
<h2 style="margin:0 0 20px;font-size:22px;color:#182016;">
                                    Sprzedający zaktualizował informacje o produkcie
                                </h2>
<p style="font-size:15px;line-height:1.7;margin:0 0 20px;">
                                    Produkt, o który pytałeś, został zaktualizowany.
                                    Sprzedający uzupełnił ogłoszenie o dodatkowe informacje,
                                    których potrzebowałeś.
                                </p>

<p style="margin:0 0 8px;font-size:13px;color:#68764B;">
                                        Produkt
                                    </p>
<p style="margin:0;font-size:17px;font-weight:bold;color:#182016;">
                                        %s
                                    </p>

<p style="font-size:15px;line-height:1.7;margin:20px 0;">
                                    Możesz teraz przejść do ogłoszenia i zapoznać się
                                    z uzupełnionymi informacjami.
                                </p>

<a href="%s" style="display:inline-block;background-color:#68764B;color:#ffffff;text-decoration:none;padding:14px 28px;border-radius:5px;font-size:15px;font-weight:bold;">
                                        Zobacz produkt
                                    </a>

<p style="font-size:14px;line-height:1.7;color:#555555;margin:25px 0 0;">
                                    Dziękujemy za korzystanie z Kasoa!
                                </p>
        """.formatted(
                safeProduct,
                escapeHtml(fullProductUrl),
                escapeHtml(frontEndUrl)
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }


    @Async
    public void sendShipmentStatusToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName,
            String shipmentStatus,
            String trackingNumber,
            String shippingMethod,
            String receiptConfirmationToken
    ) {
        String status = shipmentStatus == null
                ? "UNKNOWN"
                : shipmentStatus.trim().toUpperCase(Locale.ROOT);

        String statusLabel;
        String subject;

        switch (status) {
            case "SHIPPED" -> {
                statusLabel = "Wysłano (Shipped)";
                subject = "Twoje zamówienie #" + orderNumber + " zostało wysłane 📦";
            }
            case "DELIVERED" -> {
                statusLabel = "Dostarczono (Delivered)";
                subject = "Twoje zamówienie #" + orderNumber + " zostało dostarczone ✓";
            }
            case "RECEIVED" -> {
                statusLabel = "Odbiór potwierdzony (Received)";
                subject = "Odbiór zamówienia #" + orderNumber + " został potwierdzony ✓";
            }
            case "PROCESSING" -> {
                statusLabel = "W realizacji (Processing)";
                subject = "Aktualizacja zamówienia #" + orderNumber;
            }
            case "PAID" -> {
                statusLabel = "Opłacono (Paid)";
                subject = "Aktualizacja zamówienia #" + orderNumber;
            }
            case "CANCELLED" -> {
                statusLabel = "Anulowano (Cancelled)";
                subject = "Twoje zamówienie #" + orderNumber + " zostało anulowane";
            }
            case "PENDING" -> {
                statusLabel = "Oczekuje na realizację (Pending)";
                subject = "Aktualizacja zamówienia #" + orderNumber;
            }
            default -> {
                statusLabel = formatShipmentStatus(status);
                subject = "Aktualizacja zamówienia #" + orderNumber;
            }
        }

        String safeBuyerName = escapeHtml(
                buyerName == null || buyerName.isBlank() ? "Kliencie" : buyerName
        );
        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProductName = escapeHtml(productName);
        String safeShipmentStatus = escapeHtml(statusLabel);

        // Receipt confirmation is available only after delivery.
        String receiptConfirmationHtml = "";

        boolean isDelivered = "DELIVERED".equals(status);

        boolean hasConfirmationToken =
                receiptConfirmationToken != null
                        && !receiptConfirmationToken.isBlank();

        if (isDelivered && hasConfirmationToken) {

            String receiptConfirmationUrl =
                    deliveryConfirmationUrl
                            + "/confirm-receipt?token="
                            + URLEncoder.encode(
                            receiptConfirmationToken,
                            StandardCharsets.UTF_8
                    );

            receiptConfirmationHtml = """
            <table role="presentation" width="100%%"
                   cellpadding="0" cellspacing="0" border="0"
                   style="width:100%%;table-layout:fixed;
                          border-collapse:collapse;
                          background:#EEF1E8;
                          border:1px solid #DDE4D1;
                          border-radius:14px;
                          margin:0 0 24px 0;">
                <tr>
                    <td align="center"
                        style="padding:26px 20px;text-align:center;
                               overflow-wrap:anywhere;">

                        <div style="font-size:30px;line-height:36px;
                                    margin-bottom:10px;">
                            📦
                        </div>

                        <div style="font-size:19px;line-height:27px;
                                    font-weight:700;color:#182016;
                                    margin-bottom:10px;">
                            Otrzymałeś przesyłkę?
                        </div>

                        <div style="font-size:14px;line-height:22px;
                                    color:#596052;margin-bottom:18px;">
                            Sprawdź otrzymany przedmiot i porównaj go
                            z zamówieniem oraz opisem sprzedawcy.
                        </div>

                        <!-- 24-HOUR DEADLINE -->
                        <table role="presentation" width="100%%"
                               cellpadding="0" cellspacing="0" border="0"
                               style="width:100%%;table-layout:fixed;
                                      border-collapse:collapse;
                                      background:#FFFFFF;
                                      border:1px solid #DDE4D1;
                                      margin:0 0 20px 0;">
                            <tr>
                                <td style="padding:16px;text-align:left;
                                           overflow-wrap:anywhere;
                                           word-wrap:break-word;">

                                    <div style="font-size:14px;
                                                line-height:21px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:8px;">
                                        ⏰ Masz 24 godziny od dostawy
                                    </div>

                                    <div style="font-size:13px;
                                                line-height:21px;
                                                color:#596052;">

                                        <strong>Jeśli wszystko jest w porządku:</strong>
                                        <br>
                                        Kliknij przycisk poniżej, aby potwierdzić
                                        odbiór przedmiotu. Nie musisz logować się
                                        na swoje konto.
                                        <br><br>

                                        <strong>Jeśli zauważysz istotne rozbieżności:</strong>
                                        <br>
                                        Nie potwierdzaj odbioru. Zaloguj się na
                                        swoje konto Kasoa.pl, przejdź do
                                        <strong>Konto → Zamówienia</strong>,
                                        wybierz konkretne zamówienie i złóż
                                        wniosek o zwrot, dołączając zdjęcia
                                        lub inne dowody.
                                        <br><br>

                                        Drobne różnice wynikające na przykład
                                        z oświetlenia lub sposobu wykonania zdjęć
                                        nie stanowią same w sobie podstawy
                                        do zwrotu. Zgłoszenie powinno dotyczyć
                                        istotnych rozbieżności między ofertą
                                        a otrzymanym przedmiotem.
                                        <br><br>

                                        <strong>Jak rozpatrywany jest wniosek?</strong>
                                        <br>
                                        Wniosek nie oznacza automatycznej akceptacji
                                        zwrotu. Sprzedawca przeanalizuje zgłoszenie
                                        i przedstawione dowody. Jeśli odrzuci
                                        wniosek, sprawa zostanie przekazana
                                        administratorowi Kasoa.pl do ostatecznej
                                        oceny oferty i zgromadzonych dowodów.
                                    </div>
                                </td>
                            </tr>
                        </table>

                        <a href="%s"
                           style="display:inline-block;
                                  background:#68764B;
                                  color:#FFFFFF;
                                  text-decoration:none;
                                  font-size:15px;
                                  line-height:20px;
                                  font-weight:700;
                                  padding:14px 24px;
                                  border-radius:10px;">
                            ✓ Potwierdź odbiór przedmiotu
                        </a>

                        <div style="font-size:11px;line-height:18px;
                                    color:#7A8172;margin-top:16px;">
                            Kliknij przycisk tylko wtedy, gdy otrzymany
                            przedmiot jest zgodny z zamówieniem i opisem.
                        </div>

                        <div style="font-size:12px;line-height:20px;
                                    color:#596052;margin-top:18px;
                                    text-align:left;">
                            <strong>Ważne:</strong>
                            Jeśli w ciągu 24 godzin od dostawy nie potwierdzisz
                            odbioru ani nie złożysz wniosku o zwrot,
                            odbiór zostanie automatycznie potwierdzony,
                            a wypłata środków sprzedawcy zostanie uruchomiona.
                        </div>

                    </td>
                </tr>
            </table>
            """.formatted(escapeHtml(receiptConfirmationUrl));
        }

        // Tracking information.
        String trackingHtml = "";

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            trackingHtml = """
            <tr>
                <td style="padding:0 0 18px 0;overflow-wrap:anywhere;">
                    <div style="font-size:12px;line-height:18px;
                                color:#7A8172;margin-bottom:4px;">
                        NUMER PRZESYŁKI
                    </div>
                    <div style="font-size:15px;line-height:22px;
                                font-weight:600;color:#182016;
                                overflow-wrap:anywhere;">
                        %s
                    </div>
                </td>
            </tr>
            """.formatted(escapeHtml(trackingNumber));
        }

        // Shipping method.
        String shippingMethodHtml = "";

        if (shippingMethod != null && !shippingMethod.isBlank()) {
            shippingMethodHtml = """
            <tr>
                <td style="padding:0 0 18px 0;overflow-wrap:anywhere;">
                    <div style="font-size:12px;line-height:18px;
                                color:#7A8172;margin-bottom:4px;">
                        SPOSÓB DOSTAWY
                    </div>
                    <div style="font-size:15px;line-height:22px;
                                font-weight:600;color:#182016;
                                overflow-wrap:anywhere;">
                        %s
                    </div>
                </td>
            </tr>
            """.formatted(escapeHtml(shippingMethod));
        }

        String nextStepText = escapeHtml(getNextStepText(status));

        // Main email template.
        String html = """
<div style="font-size:24px;line-height:32px;
                                            font-weight:700;color:#182016;
                                            margin-bottom:8px;">
                                    Cześć %s! 👋
                                </div>
<div style="font-size:15px;line-height:24px;
                                            color:#596052;margin-bottom:24px;">
                                    Mamy aktualizację dotyczącą Twojego
                                    zamówienia.
                                </div>




                                            %s
                                        






<div style="font-size:12px;
                                                        line-height:18px;
                                                        color:#7A8172;
                                                        margin-bottom:5px;">
                                                ZAMÓWIENIE
                                            </div>
<div style="font-size:20px;
                                                        line-height:27px;
                                                        font-weight:700;
                                                        color:#182016;
                                                        margin-bottom:20px;
                                                        overflow-wrap:anywhere;">
                                                #%s
                                            </div>
<div style="font-size:12px;
                                                        line-height:18px;
                                                        color:#7A8172;
                                                        margin-bottom:5px;">
                                                PRZEDMIOT
                                            </div>
<div style="font-size:16px;
                                                        line-height:23px;
                                                        font-weight:600;
                                                        color:#182016;
                                                        overflow-wrap:anywhere;">
                                                %s
                                            </div>







<div style="font-size:17px;
                                                        line-height:24px;
                                                        font-weight:700;
                                                        color:#182016;
                                                        margin-bottom:20px;">
                                                📦 Informacje o przesyłce
                                            </div>

                                                %s
                                                %s
                                            




                                %s

                                



<div style="font-size:17px;
                                                        line-height:24px;
                                                        font-weight:700;
                                                        color:#182016;
                                                        margin-bottom:10px;">
                                                🔒 Bezpieczna transakcja
                                            </div>
<div style="font-size:14px;
                                                        line-height:22px;
                                                        color:#596052;">
                                                Płatność jest zabezpieczona
                                                w systemie Kasoa.pl.
                                                Środki zostaną rozliczone
                                                zgodnie z zasadami transakcji
                                                po zakończeniu procesu.
                                            </div>







<div style="font-size:17px;
                                                        line-height:24px;
                                                        font-weight:700;
                                                        color:#182016;
                                                        margin-bottom:12px;">
                                                Co dalej?
                                            </div>
<div style="font-size:14px;
                                                        line-height:23px;
                                                        color:#4F5849;">
                                                %s
                                            </div>







<a href="https://www.kasoa.pl" style="display:inline-block;
                                                      background:#68764B;
                                                      color:#FFFFFF;
                                                      text-decoration:none;
                                                      font-size:15px;
                                                      line-height:20px;
                                                      font-weight:700;
                                                      padding:14px 28px;
                                                      border-radius:10px;">
                                                Przejdź do kasoa.pl →
                                            </a>
        """.formatted(
                safeBuyerName,
                safeShipmentStatus,
                safeOrderNumber,
                safeProductName,
                trackingHtml,
                shippingMethodHtml,
                receiptConfirmationHtml,
                nextStepText
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(html));
    }

    /**
     * Buyer rejects item for personal collections
     * @param buyerEmail
     * @param buyerName
     * @param orderNumber
     * @param productName
     */
    @Async
    public void sendRejectionStatusToBuyer(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName
    ) {
        if (buyerEmail == null || buyerEmail.isBlank()) {
            return;
        }

        String safeBuyerName = escapeHtml(
                buyerName == null || buyerName.isBlank()
                        ? "Kliencie"
                        : buyerName
        );

        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProductName = escapeHtml(productName);

        String subject = "Odbiór osobisty nie został zrealizowany – zamówienie "
                + (orderNumber == null ? "" : orderNumber);

        String ordersUrl = "https://kasoa.pl/account/orders";

        String html = """
<h2 style="margin-top:0; color:#333333;
                                                   font-size:21px;">
                                            Odbiór osobisty nie został zrealizowany
                                        </h2>
<p style="font-size:15px; line-height:1.6;">
                                            Dzień dobry %s,
                                        </p>
<p style="font-size:15px; line-height:1.6;">
                                            Sprzedający poinformował nas, że odbiór
                                            osobisty przedmiotu nie doszedł do skutku.
                                        </p>



<strong>Numer zamówienia:</strong>
                                                    %s<br/>
<strong>Przedmiot:</strong>
                                                    %s
                                                


<h3 style="font-size:17px; color:#333333;">
                                            Co należy teraz zrobić?
                                        </h3>
<p style="font-size:15px; line-height:1.6;">
                                            Zaloguj się na swoje konto Kasoa i przejdź
                                            do sekcji <strong>Moje konto → Zamówienia</strong>.
                                            Następnie zainicjuj zgłoszenie zwrotu dla
                                            tego zamówienia.
                                        </p>
<p style="font-size:15px; line-height:1.6;">
                                            W zgłoszeniu:
                                        </p>
<ul style="font-size:15px; line-height:1.8;
                                                   padding-left:22px;">
<li>wybierz odpowiedni powód zgłoszenia,</li>
<li>opisz, co wydarzyło się podczas próby odbioru,</li>
<li>dołącz zdjęcia lub inne dostępne dowody,
                                                jeśli je posiadasz.</li>
</ul>
<p style="font-size:15px; line-height:1.6;">
                                            Sprzedający będzie mógł odnieść się do
                                            zgłoszenia. Jeżeli nie zgodzi się z jego
                                            treścią, sprawa zostanie przekazana do
                                            weryfikacji przez Kasoa.
                                        </p>
<p style="font-size:15px; line-height:1.6;">
                                            Samo zgłoszenie nie oznacza automatycznego
                                            zatwierdzenia zwrotu. Sprawa zostanie
                                            rozpatrzona na podstawie przedstawionych
                                            informacji i dostępnych dowodów.
                                        </p>

<a href="%s" style="display:inline-block;
                                                      background-color:#66704A;
                                                      color:#ffffff;
                                                      text-decoration:none;
                                                      padding:14px 28px;
                                                      border-radius:5px;
                                                      font-size:15px;
                                                      font-weight:bold;">
                                                Przejdź do moich zamówień
                                            </a>

<p style="font-size:13px; line-height:1.6;
                                                  color:#777777;">
                                            Jeśli masz pytania, skontaktuj się z
                                            naszym zespołem obsługi klienta.
                                        </p>
<p style="font-size:15px; line-height:1.6;">
                                            Pozdrawiamy,<br/>
<strong>Zespół Kasoa</strong>
</p>
        """.formatted(
                safeBuyerName,
                safeOrderNumber,
                safeProductName,
                ordersUrl
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(html));
    }








    public void sendShipmentStatusToSeller(
            String buyerEmail,
            String buyerName,
            String orderNumber,
            String productName,
            String shipmentStatus,
            String trackingNumber,
            String shippingMethod,
            String receiptConfirmationToken
    ) {

        String safeBuyerName = escapeHtml(buyerName);
        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProductName = escapeHtml(productName);
        String safeShipmentStatus = escapeHtml(formatShipmentStatus(shipmentStatus));
        String safeTrackingNumber = escapeHtml(trackingNumber);
        String safeShippingMethod = escapeHtml(shippingMethod);

        String subject;

        switch (shipmentStatus.toUpperCase()) {

            case "SHIPPED" ->
                    subject = "Twoje zamówienie #" + orderNumber
                            + " zostało wysłane 📦";

            case "DELIVERED" ->
                    subject = "Twoje zamówienie #" + orderNumber
                            + " zostało dostarczone ✓";

            case "RECEIVED" ->
                    subject = "Odbiór zamówienia #" + orderNumber
                            + " został potwierdzony ✓";

            default ->
                    subject = "Aktualizacja zamówienia #" + orderNumber;
        }


        /*
         * ============================================================
         * RECEIPT CONFIRMATION
         * ============================================================
         *
         * The confirmation button is displayed for:
         *
         * SHIPPED
         * DELIVERED
         *
         * The URL does NOT directly confirm the order.
         * It opens a confirmation page where the buyer must explicitly
         * confirm receipt.
         */

        String receiptConfirmationHtml = "";

        boolean confirmationStatus =
                "SHIPPED".equalsIgnoreCase(shipmentStatus)
                        || "DELIVERED".equalsIgnoreCase(shipmentStatus);

        boolean hasConfirmationToken =
                receiptConfirmationToken != null
                        && !receiptConfirmationToken.isBlank();

        if (confirmationStatus && hasConfirmationToken) {

            String receiptConfirmationUrl =
                    returnConfirmationUrl
                            + "/confirm-receipt?token="
                            + URLEncoder.encode(
                            receiptConfirmationToken,
                            StandardCharsets.UTF_8
                    );

            String confirmationTitle;

            if ("DELIVERED".equalsIgnoreCase(shipmentStatus)) {
                confirmationTitle = "Przesyłka została dostarczona";
            } else {
                confirmationTitle = "Otrzymałeś przesyłkę?";
            }

            receiptConfirmationHtml = """
            <table width="100%%"
                   cellpadding="0"
                   cellspacing="0"
                   border="0"
                   style="
                       width:100%%;
                       background:#EEF1E8;
                       border:1px solid #DDE4D1;
                       border-radius:14px;
                       margin:0 0 24px 0;
                   ">

                <tr>
                    <td style="
                        padding:26px 24px;
                        text-align:center;
                    ">

                        <div style="
                            font-size:30px;
                            line-height:36px;
                            margin-bottom:10px;
                        ">
                            📦
                        </div>

                        <div style="
                            font-size:18px;
                            line-height:26px;
                            font-weight:700;
                            color:#182016;
                            margin-bottom:10px;
                        ">
                            %s
                        </div>

                        <div style="
                            font-size:14px;
                            line-height:22px;
                            color:#596052;
                            margin-bottom:20px;
                        ">
                            Otrzymałeś przesyłkę?
                            Sprawdź przedmiot i jeśli wszystko
                            jest zgodne z zamówieniem, potwierdź
                            odbiór.
                        </div>

                        <a href="%s"
                           style="
                               display:inline-block;
                               background:#68764B;
                               color:#FFFFFF;
                               text-decoration:none;
                               font-size:15px;
                               line-height:20px;
                               font-weight:700;
                               padding:14px 24px;
                               border-radius:10px;
                           ">
                            ✓ Potwierdź odbiór przedmiotu
                        </a>

                        <div style="
                            font-size:11px;
                            line-height:18px;
                            color:#7A8172;
                            margin-top:16px;
                        ">
                            Potwierdź odbiór dopiero po otrzymaniu
                            i sprawdzeniu przesyłki.
                        </div>

                    </td>
                </tr>

            </table>
            """.formatted(
                    escapeHtml(confirmationTitle),
                    escapeHtml(receiptConfirmationUrl)
            );
        }


        /*
         * ============================================================
         * TRACKING INFORMATION
         * ============================================================
         */

        String trackingHtml = "";

        if (trackingNumber != null && !trackingNumber.isBlank()) {

            trackingHtml = """
            <tr>
                <td style="
                    padding:0 0 18px 0;
                ">

                    <div style="
                        font-size:12px;
                        line-height:18px;
                        color:#7A8172;
                        margin-bottom:4px;
                    ">
                        NUMER PRZESYŁKI
                    </div>

                    <div style="
                        font-size:15px;
                        line-height:22px;
                        font-weight:600;
                        color:#182016;
                    ">
                        %s
                    </div>

                </td>
            </tr>
            """.formatted(
                    safeTrackingNumber
            );
        }


        /*
         * ============================================================
         * SHIPPING METHOD
         * ============================================================
         */

        String shippingMethodHtml = "";

        if (shippingMethod != null && !shippingMethod.isBlank()) {

            shippingMethodHtml = """
            <tr>
                <td style="
                    padding:0 0 18px 0;
                ">

                    <div style="
                        font-size:12px;
                        line-height:18px;
                        color:#7A8172;
                        margin-bottom:4px;
                    ">
                        SPOSÓB DOSTAWY
                    </div>

                    <div style="
                        font-size:15px;
                        line-height:22px;
                        font-weight:600;
                        color:#182016;
                    ">
                        %s
                    </div>

                </td>
            </tr>
            """.formatted(
                    safeShippingMethod
            );
        }


        /*
         * ============================================================
         * NEXT STEP
         * ============================================================
         */

        String nextStepText = getNextStepText(shipmentStatus);


        /*
         * ============================================================
         * MAIN EMAIL
         * ============================================================
         */

        String html = """
<div style="
                                    font-size:24px;
                                    line-height:32px;
                                    font-weight:700;
                                    color:#182016;
                                    margin-bottom:8px;
                                ">
                                    Cześć %s! 👋
                                </div>
<div style="
                                    font-size:15px;
                                    line-height:24px;
                                    color:#596052;
                                    margin-bottom:24px;
                                ">
                                    Mamy aktualizację dotyczącą Twojego
                                    zamówienia.
                                </div>



<table border="0" cellpadding="0" cellspacing="0" style="
                                           margin:0 0 24px 0;
                                       ">
<tr>
<td style="
                                            background:#EEF1E8;
                                            border-radius:20px;
                                            padding:8px 14px;
                                            font-size:13px;
                                            line-height:18px;
                                            font-weight:700;
                                            color:#68764B;
                                        ">
                                            %s
                                        </td>
</tr>
</table>






<div style="
                                                font-size:12px;
                                                line-height:18px;
                                                color:#7A8172;
                                                margin-bottom:5px;
                                            ">
                                                ZAMÓWIENIE
                                            </div>
<div style="
                                                font-size:20px;
                                                line-height:27px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:20px;
                                            ">
                                                #%s
                                            </div>
<div style="
                                                font-size:12px;
                                                line-height:18px;
                                                color:#7A8172;
                                                margin-bottom:5px;
                                            ">
                                                PRZEDMIOT
                                            </div>
<div style="
                                                font-size:16px;
                                                line-height:23px;
                                                font-weight:600;
                                                color:#182016;
                                            ">
                                                %s
                                            </div>









<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:20px;
                                            ">
                                                📦 Informacje o przesyłce
                                            </div>


                                                %s
                                                %s

                                            







                                %s


                                





<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:10px;
                                            ">
                                                🔒 Bezpieczna transakcja
                                            </div>
<div style="
                                                font-size:14px;
                                                line-height:22px;
                                                color:#596052;
                                            ">
                                                Płatność jest zabezpieczona
                                                w systemie kasoa.pl.
                                                Środki zostaną rozliczone
                                                zgodnie z zasadami transakcji
                                                po zakończeniu procesu.
                                            </div>









<div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:12px;
                                            ">
                                                Co dalej?
                                            </div>
<div style="
                                                font-size:14px;
                                                line-height:23px;
                                                color:#4F5849;
                                            ">
                                                %s
                                            </div>









<a href="https://www.kasoa.pl" style="
                                                   display:inline-block;
                                                   background:#68764B;
                                                   color:#FFFFFF;
                                                   text-decoration:none;
                                                   font-size:15px;
                                                   line-height:20px;
                                                   font-weight:700;
                                                   padding:14px 28px;
                                                   border-radius:10px;
                                               ">
                                                Przejdź do kasoa.pl →
                                            </a>
        """.formatted(
                safeBuyerName,
                safeShipmentStatus,
                safeOrderNumber,
                safeProductName,
                trackingHtml,
                shippingMethodHtml,
                receiptConfirmationHtml,
                nextStepText
        );


        sendHtmlEmail(
                buyerEmail,
                subject,
                buildEmailLayout(html)
        );
    }


    public void notifySellerToStartVerification(String email, String name,String accountUrl) {

        String safeName = escapeHtml(
                name != null && !name.isBlank() ? name : "Sprzedawco"
        );

        String subject =
                "Dokończ weryfikację, aby otrzymywać wypłaty – kasoa.pl";


        String html = """
<div style="font-size:24px;line-height:32px;
                                        font-weight:700;color:#182016;
                                        margin-bottom:12px;">
                                Cześć %s! 👋
                            </div>
<div style="font-size:15px;line-height:24px;
                                        color:#596052;margin-bottom:24px;">
                                Aby móc otrzymywać wypłaty ze sprzedaży
                                na kasoa.pl, musisz rozpocząć proces
                                weryfikacji tożsamości sprzedawcy.
                            </div>




<div style="font-size:18px;line-height:26px;
                                                    font-weight:700;color:#182016;
                                                    margin-bottom:12px;">
                                            🔐 Weryfikacja tożsamości sprzedawcy
                                        </div>
<div style="font-size:14px;line-height:23px;
                                                    color:#596052;">
                                            Weryfikacja jest wymagana, aby umożliwić
                                            realizację wypłat. Rozpocznij onboarding
                                            i wykonaj wymagane kroki zgodnie
                                            z wyświetlanymi instrukcjami.
                                        </div>







<div style="font-size:17px;line-height:24px;
                                                    font-weight:700;color:#182016;
                                                    margin-bottom:12px;">
                                            Jak rozpocząć weryfikację?
                                        </div>
<div style="font-size:14px;line-height:24px;
                                                    color:#4F5849;">
<strong>1.</strong> Zaloguj się na swoje
                                            konto kasoa.pl.<br/><br/>
<strong>2.</strong> Przejdź do sekcji
                                            <strong>Konto → Profil</strong>.<br/><br/>
<strong>3.</strong> Otwórz sekcję
                                            <strong>Weryfikacja tożsamości
                                            sprzedawcy</strong>.<br/><br/>
<strong>4.</strong> Kliknij przycisk
                                            rozpoczynający onboarding i postępuj
                                            zgodnie z instrukcjami.
                                        </div>







<div style="font-size:15px;line-height:22px;
                                                    font-weight:700;color:#182016;
                                                    margin-bottom:8px;">
                                            Ważne informacje o wypłatach
                                        </div>
<div style="font-size:14px;line-height:23px;
                                                    color:#596052;">
                                            Do czasu ukończenia wymaganej weryfikacji
                                            i uzyskania odpowiedniego statusu
                                            zatwierdzenia możliwość otrzymywania
                                            wypłat może być niedostępna.
                                        </div>







<a href="%s" style="display:inline-block;
                                                  background:#68764B;
                                                  color:#FFFFFF;
                                                  text-decoration:none;
                                                  font-size:15px;line-height:20px;
                                                  font-weight:700;
                                                  padding:14px 28px;
                                                  border-radius:10px;">
                                            Przejdź do swojego konta →
                                        </a>



<div style="font-size:13px;line-height:21px;
                                        color:#7A8172;text-align:center;
                                        margin-top:18px;">
                                Jeśli rozpocząłeś już weryfikację,
                                dokończ proces, aby umożliwić realizację wypłat.
                            </div>
        """.formatted(
                safeName,
                escapeHtml(accountUrl)
        );

        sendHtmlEmail(email, subject, buildEmailLayout(html));
    }



    private String formatAddress(
            String label,
            String name,
            String address,
            String phone
    ) {
        String[] lines = address != null
                ? address.split("\\R")
                : new String[0];

        StringBuilder html = new StringBuilder();

        html.append("""
        <tr>
            <td colspan="2"
                style="
                    padding:4px 0 10px 0;
                    font-size:14px;
                    font-weight:800;
                    color:#68764B;
                ">
                %s
            </td>
        </tr>
        """.formatted(
                escapeHtml(label)
        ));

        html.append("""
        <tr>
            <td colspan="2"
                style="
                    padding:2px 0;
                    font-size:15px;
                    line-height:22px;
                    font-weight:700;
                    color:#182016;
                ">
                %s
            </td>
        </tr>
        """.formatted(
                escapeHtml(name)
        ));

        for (String line : lines) {

            if (line == null || line.isBlank()) {
                continue;
            }

            html.append("""
            <tr>
                <td colspan="2"
                    style="
                        padding:2px 0;
                        font-size:15px;
                        line-height:22px;
                        color:#182016;
                    ">
                    %s
                </td>
            </tr>
            """.formatted(
                    escapeHtml(line.trim())
            ));
        }

        if (phone != null && !phone.isBlank()) {

            html.append("""
            <tr>
                <td colspan="2"
                    style="
                        padding:2px 0;
                        font-size:15px;
                        line-height:22px;
                        color:#182016;
                    ">
                    %s
                </td>
            </tr>
            """.formatted(
                    escapeHtml(phone)
            ));
        }

        return html.toString();
    }


    private String formatShipmentStatus(String shipmentStatus) {
        if (shipmentStatus == null || shipmentStatus.isBlank()) {
            return "Nieznany status (Unknown status)";
        }

        String status = shipmentStatus.trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "SHIPPED" -> "Wysłano (Shipped)";
            case "DELIVERED" -> "Dostarczono (Delivered)";
            case "RECEIVED" -> "Odbiór potwierdzony (Received)";
            case "PROCESSING" -> "W realizacji (Processing)";
            case "PAID" -> "Opłacono (Paid)";
            case "CANCELLED" -> "Anulowano (Cancelled)";
            case "PENDING" -> "Oczekuje (Pending)";
            case "APPROVED" -> "Zatwierdzono (Approved)";
            case "REJECTED" -> "Odrzucono (Rejected)";
            case "UNKNOWN" -> "Nieznany status (Unknown)";
            default -> "Zaktualizowano (Updated): " + shipmentStatus;
        };
    }

    private String getNextStepText(String shipmentStatus) {

        if (shipmentStatus == null) {
            return "Sprawdź aktualny status swojego zamówienia na kasoa.pl.";
        }

        return switch (shipmentStatus.toUpperCase()) {

            case "SHIPPED" ->
                    "Przesyłka jest już w drodze. "
                            + "Po otrzymaniu paczki sprawdź przedmiot "
                            + "i potwierdź odbiór za pomocą przycisku powyżej.";

            case "DELIVERED" ->
                    "Przesyłka została oznaczona jako dostarczona. "
                            + "Sprawdź przedmiot i potwierdź odbiór zamówienia.";

            case "RECEIVED" ->
                    "Odbiór zamówienia został potwierdzony. "
                            + "Dziękujemy za korzystanie z kasoa.pl.";

            default ->
                    "Sprawdź aktualny status swojego zamówienia na kasoa.pl.";
        };
    }


    /**
     * Notify seller following transfer to connected account
     * @param email
     * @param sellerPrice
     * @param orderNumber
     * @param transferId
     * @param amountToRecover
     * @param stripeFee
     */
    @Async
    public void sendTransferNotification(
            String email,
            BigDecimal sellerPrice,
            String orderNumber,
            String transferId,
            BigDecimal amountToRecover,
            BigDecimal stripeFee) {

        try {
            NumberFormat currencyFormat =
                    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pl-PL"));

            String safeOrderNumber = escapeHtml(
                    orderNumber != null ? orderNumber : "—"
            );

            BigDecimal safeStripeFeeValue = stripeFee != null
                    ? stripeFee
                    : BigDecimal.ZERO;

            BigDecimal safeAmountToRecoverValue = amountToRecover != null
                    ? amountToRecover
                    : BigDecimal.ZERO;

            String safeSellerPrice = escapeHtml(
                    sellerPrice != null
                            ? currencyFormat.format(sellerPrice)
                            : "—"
            );

            String safeStripeFee = escapeHtml(
                    currencyFormat.format(safeStripeFeeValue)
            );

            String safeAmountToRecover = escapeHtml(
                    currencyFormat.format(safeAmountToRecoverValue)
            );

            BigDecimal netAmount = sellerPrice != null
                    ? sellerPrice.subtract(safeStripeFeeValue)
                    .subtract(safeAmountToRecoverValue)
                    : null;

            String safeNetAmount = escapeHtml(
                    netAmount != null
                            ? currencyFormat.format(netAmount)
                            : "—"
            );

            String safeTransferId = escapeHtml(
                    transferId != null ? transferId : "—"
            );

            String html = """
<h2 style="margin:0 0 20px; font-size:22px; color:#182016;">
                                                Przekazaliśmy środki ze sprzedaży
                                            </h2>
<p style="margin:0 0 16px; font-size:15px; line-height:1.6; color:#596052;">
                                                Środki należne Ci za zamówienie zostały przekazane na Twoje konto Stripe.
                                            </p>
<p style="margin:0 0 24px; font-size:15px; line-height:1.6; color:#596052;">
                                                Poniżej znajdziesz szczegółowe rozliczenie:
                                            </p>



                                                        Numer zamówienia
                                                    

                                                        %s
                                                    



                                                        Kwota brutto sprzedającego
                                                    

                                                        %s
                                                    



                                                        Opłata Stripe za bieżące zamówienie
                                                    

                                                        − %s
                                                    



                                                        Nieodzyskane opłaty Stripe z anulowanych zamówień
                                                    

                                                        − %s
                                                    



                                                        Kwota netto przekazana
                                                    

                                                        %s
                                                    


<p style="margin:24px 0 8px; font-size:13px; color:#596052;">
                                                Identyfikator transferu Stripe:
                                            </p>
<p style="margin:0; font-size:12px; color:#596052; word-break:break-all;">
                                                %s
                                            </p>
<p style="margin:28px 0 0; font-size:14px; line-height:1.6; color:#596052;">
                                                Możesz sprawdzić szczegóły przekazania środków na swoim koncie Stripe.
                                            </p>
        """.formatted(
                    safeOrderNumber,
                    safeSellerPrice,
                    safeStripeFee,
                    safeAmountToRecover,
                    safeNetAmount,
                    safeTransferId
            );

            sendHtmlEmail(
                    email,
                    "Przekazaliśmy środki za zamówienie " + safeOrderNumber + " – kasoa.pl",
                    buildEmailLayout(html)
            );

        } catch (Exception e) {
            log.error("Failed to send transfer notification to {}", email, e);
        }
    }




}
