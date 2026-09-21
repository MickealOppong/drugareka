package com.pages.service;


import com.pages.dto.EmailProductItemDto;
import com.pages.enums.ShipmentStatus;
import com.pages.model.ListingOrderItem;
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

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;



@Slf4j
@Service
public class EmailNotificationService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name:kasoa.pl}")
    private String fromName;

    public EmailNotificationService() {
        this.restTemplate = new RestTemplate();
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

        // Order items
        StringBuilder itemsHtml = new StringBuilder();

        if (items != null) {
            for (EmailProductItemDto item : items) {
                String productName = escapeHtml(item.getProductName());
                String amount = item.getAmount() != null
                        ? item.getAmount().toPlainString()
                        : "0.00";

                itemsHtml.append("""
                <tr>
                    <td style="
                        padding: 14px 0;
                        border-bottom: 1px dashed #DDE2D8;
                        font-size: 14px;
                        line-height: 1.5;
                        color: #182016;
                        font-weight: 600;
                    ">
                        %s
                    </td>

                    <td
                        align="right"
                        style="
                            padding: 14px 0 14px 12px;
                            border-bottom: 1px dashed #DDE2D8;
                            font-size: 14px;
                            color: #68764B;
                            font-weight: 700;
                            white-space: nowrap;
                        "
                    >
                        %s zł
                    </td>
                </tr>
                """.formatted(productName, amount));
            }
        }

        String total = totalAmount != null
                ? totalAmount.toPlainString()
                : "0.00";

        String htmlContent = """
        <div style="
            margin: 0;
            padding: 30px 15px;
            background-color: #F5F6F1;
            font-family: Arial, Helvetica, sans-serif;
            color: #182016;
        ">
            <table
                width="100%%"
                cellpadding="0"
                cellspacing="0"
                border="0"
                style="max-width: 600px; margin: 0 auto;"
            >

                <!-- HEADER -->
                <tr>
                    <td style="
                        background-color: #68764B;
                        padding: 28px 30px;
                        border-radius: 16px 16px 0 0;
                    ">
                        <div style="
                            font-size: 26px;
                            line-height: 1;
                            font-weight: 800;
                            letter-spacing: -1px;
                            color: #FFFFFF;
                        ">
                            kasoa<span style="
                                font-weight: 400;
                                opacity: 0.75;
                            ">.pl</span>
                        </div>

                        <div style="
                            margin-top: 10px;
                            font-size: 12px;
                            color: #E9EDDF;
                            letter-spacing: 0.5px;
                        ">
                            DRUGA RĘKA · BEZPIECZNE ZAKUPY
                        </div>
                    </td>
                </tr>

                <!-- BODY -->
                <tr>
                    <td style="
                        background-color: #FFFFFF;
                        padding: 32px 30px 30px 30px;
                    ">

                        <!-- SUCCESS BADGE -->
                        <div style="
                            display: inline-block;
                            padding: 8px 13px;
                            background-color: #EEF1E8;
                            color: #68764B;
                            border-radius: 20px;
                            font-size: 11px;
                            font-weight: 800;
                            letter-spacing: 0.6px;
                            text-transform: uppercase;
                        ">
                            ✓ Płatność potwierdzona
                        </div>

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
                            Zamówienie jest teraz bezpiecznie przekazywane
                            do realizacji.
                        </p>

                        <!-- ORDER NUMBER -->
                        <table
                            width="100%%"
                            cellpadding="0"
                            cellspacing="0"
                            border="0"
                            style="
                                background-color: #F7F8F5;
                                border: 1px solid #E4E8DE;
                                border-radius: 12px;
                                margin-bottom: 18px;
                            "
                        >
                            <tr>
                                <td style="padding: 18px 20px;">

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

                                </td>
                            </tr>
                        </table>

                        <!-- ORDER SUMMARY -->
                        <table
                            width="100%%"
                            cellpadding="0"
                            cellspacing="0"
                            border="0"
                            style="
                                background-color: #FFFFFF;
                                border: 1px solid #E4E8DE;
                                border-radius: 12px;
                                overflow: hidden;
                            "
                        >
                            <tr>
                                <td style="padding: 20px 20px 8px 20px;">

                                    <div style="
                                        font-size: 10px;
                                        font-weight: 700;
                                        color: #8A9288;
                                        text-transform: uppercase;
                                        letter-spacing: 1px;
                                    ">
                                        Podsumowanie zamówienia
                                    </div>

                                </td>
                            </tr>

                            <tr>
                                <td style="padding: 0 20px 12px 20px;">

                                    <table
                                        width="100%%"
                                        cellpadding="0"
                                        cellspacing="0"
                                        border="0"
                                    >
                                        %s
                                    </table>

                                </td>
                            </tr>

                            <!-- TOTAL -->
                            <tr>
                                <td style="
                                    padding: 16px 20px 20px 20px;
                                    border-top: 1px solid #E4E8DE;
                                ">
                                    <table
                                        width="100%%"
                                        cellpadding="0"
                                        cellspacing="0"
                                        border="0"
                                    >
                                        <tr>
                                            <td style="
                                                font-size: 14px;
                                                color: #182016;
                                                font-weight: 700;
                                            ">
                                                Razem z dostawą
                                            </td>

                                            <td
                                                align="right"
                                                style="
                                                    font-size: 21px;
                                                    color: #68764B;
                                                    font-weight: 800;
                                                    white-space: nowrap;
                                                "
                                            >
                                                %s zł
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>

                        <!-- ESCROW -->
                        <table
                            width="100%%"
                            cellpadding="0"
                            cellspacing="0"
                            border="0"
                            style="
                                margin-top: 18px;
                                background-color: #EEF1E8;
                                border-radius: 12px;
                            "
                        >
                            <tr>
                                <td
                                    width="42"
                                    valign="top"
                                    style="
                                        padding: 19px 0 19px 18px;
                                        font-size: 22px;
                                    "
                                >
                                    🛡️
                                </td>

                                <td style="
                                    padding: 19px 18px 19px 8px;
                                ">
                                    <div style="
                                        font-size: 13px;
                                        font-weight: 800;
                                        color: #68764B;
                                        margin-bottom: 6px;
                                    ">
                                        Bezpieczna ochrona kasoa.pl
                                    </div>

                                    <div style="
                                        font-size: 13px;
                                        line-height: 1.6;
                                        color: #536047;
                                    ">
                                        Twoje środki są zabezpieczone w systemie
                                        ochrony kupujących Kasoa Escrow.
                                        Otrzymasz kolejne informacje, gdy
                                        sprzedawcy rozpoczną realizację
                                        zamówienia.
                                    </div>
                                </td>
                            </tr>
                        </table>

                        <!-- WHAT'S NEXT -->
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

                            <table
                                width="100%%"
                                cellpadding="0"
                                cellspacing="0"
                                border="0"
                            >
                                <tr>
                                    <td
                                        width="32"
                                        valign="top"
                                        style="padding-bottom: 13px;"
                                    >
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
                                        padding: 3px 0 13px 8px;
                                        font-size: 13px;
                                        line-height: 1.5;
                                        color: #667066;
                                    ">
                                        Sprzedawca przygotuje Twoje
                                        zamówienie do wysyłki.
                                    </td>
                                </tr>

                                <tr>
                                    <td
                                        width="32"
                                        valign="top"
                                        style="padding-bottom: 13px;"
                                    >
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
                                        padding: 3px 0 13px 8px;
                                        font-size: 13px;
                                        line-height: 1.5;
                                        color: #667066;
                                    ">
                                        Otrzymasz numer śledzenia,
                                        gdy przesyłka zostanie nadana.
                                    </td>
                                </tr>

                                <tr>
                                    <td width="32" valign="top">
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
                                        line-height: 1.5;
                                        color: #667066;
                                    ">
                                        Śledź przesyłkę i odbierz swoje
                                        zakupy.
                                    </td>
                                </tr>
                            </table>
                        </div>

                        <!-- CTA -->
                        <div style="
                            margin-top: 28px;
                            text-align: center;
                        ">
                            <a
                                href="https://kasoa.pl"
                                style="
                                    display: inline-block;
                                    padding: 14px 28px;
                                    background-color: #68764B;
                                    color: #FFFFFF;
                                    text-decoration: none;
                                    border-radius: 9px;
                                    font-size: 13px;
                                    font-weight: 700;
                                "
                            >
                                Sprawdź moje zamówienie →
                            </a>
                        </div>

                    </td>
                </tr>

                <!-- FOOTER -->
                <tr>
                    <td style="
                        background-color: #182016;
                        padding: 24px 30px;
                        border-radius: 0 0 16px 16px;
                        text-align: center;
                    ">
                        <div style="
                            font-size: 17px;
                            font-weight: 800;
                            color: #FFFFFF;
                        ">
                            kasoa<span style="
                                font-weight: 400;
                                color: #AEB69D;
                            ">.pl</span>
                        </div>

                        <div style="
                            margin-top: 7px;
                            font-size: 11px;
                            color: #AEB69D;
                        ">
                            Good things. A second chance.
                        </div>

                        <div style="
                            margin-top: 14px;
                            font-size: 10px;
                            line-height: 1.5;
                            color: #7F8878;
                        ">
                            Ta wiadomość została wygenerowana automatycznie.
                            <br>
                            Status przesyłek możesz śledzić
                            w zakładce „Zamówienia”.
                        </div>
                    </td>
                </tr>

            </table>
        </div>
        """.formatted(
                escapeHtml(buyerName),
                escapeHtml(orderNumber),
                escapeHtml(orderNumber),
                itemsHtml.toString(),
                total
        );

        sendHtmlEmail(buyerEmail, subject, htmlContent);
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

        String htmlContent = """
        <div style="
            margin: 0;
            padding: 30px 15px;
            background-color: #F5F6F1;
            font-family: Arial, Helvetica, sans-serif;
            color: #182016;
        ">
            <table
                width="100%%"
                cellpadding="0"
                cellspacing="0"
                border="0"
                style="max-width: 600px; margin: 0 auto;"
            >
                <tr>
                    <td
                        style="
                            background-color: #68764B;
                            padding: 28px 30px;
                            border-radius: 16px 16px 0 0;
                        "
                    >
                        <div style="
                            font-size: 26px;
                            line-height: 1;
                            font-weight: 800;
                            letter-spacing: -1px;
                            color: #FFFFFF;
                        ">
                            kasoa<span style="
                                font-weight: 400;
                                opacity: 0.75;
                            ">.pl</span>
                        </div>

                        <div style="
                            margin-top: 10px;
                            font-size: 12px;
                            color: #E9EDDF;
                            letter-spacing: 0.5px;
                        ">
                            DRUGA RĘKA · DOBRE RZECZY
                        </div>
                    </td>
                </tr>

                <tr>
                    <td
                        style="
                            background-color: #FFFFFF;
                            padding: 35px 30px 30px 30px;
                        "
                    >
                        <div style="
                            display: inline-block;
                            padding: 7px 12px;
                            background-color: #EEF1E8;
                            color: #68764B;
                            border-radius: 20px;
                            font-size: 11px;
                            font-weight: 700;
                            letter-spacing: 0.5px;
                            text-transform: uppercase;
                        ">
                            ✓ Oferta opublikowana
                        </div>

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

                        <!-- Product card -->
                        <table
                            width="100%%"
                            cellpadding="0"
                            cellspacing="0"
                            border="0"
                            style="
                                background-color: #F7F8F5;
                                border: 1px solid #E4E8DE;
                                border-radius: 12px;
                            "
                        >
                            <tr>
                                <td style="padding: 20px 22px;">
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
                                </td>
                            </tr>
                        </table>

                        <!-- Success message -->
                        <table
                            width="100%%"
                            cellpadding="0"
                            cellspacing="0"
                            border="0"
                            style="
                                margin-top: 18px;
                                background-color: #EEF1E8;
                                border-radius: 12px;
                            "
                        >
                            <tr>
                                <td
                                    width="42"
                                    valign="top"
                                    style="
                                        padding: 18px 0 18px 18px;
                                        font-size: 22px;
                                    "
                                >
                                    💡
                                </td>

                                <td style="padding: 18px 18px 18px 10px;">
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
                                </td>
                            </tr>
                        </table>

                        <!-- Next steps -->
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

                            <table
                                width="100%%"
                                cellpadding="0"
                                cellspacing="0"
                                border="0"
                            >
                                <tr>
                                    <td
                                        width="32"
                                        valign="top"
                                        style="padding-bottom: 12px;"
                                    >
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

                                    <td
                                        valign="top"
                                        style="
                                            padding: 3px 0 12px 8px;
                                            font-size: 13px;
                                            color: #667066;
                                        "
                                    >
                                        Poczekaj na zainteresowanego kupującego.
                                    </td>
                                </tr>

                                <tr>
                                    <td
                                        width="32"
                                        valign="top"
                                        style="padding-bottom: 12px;"
                                    >
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

                                    <td
                                        valign="top"
                                        style="
                                            padding: 3px 0 12px 8px;
                                            font-size: 13px;
                                            color: #667066;
                                        "
                                    >
                                        Po sprzedaży otrzymasz od nas
                                        wszystkie informacje dotyczące wysyłki.
                                    </td>
                                </tr>

                                <tr>
                                    <td width="32" valign="top">
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

                                    <td
                                        valign="top"
                                        style="
                                            padding: 3px 0 0 8px;
                                            font-size: 13px;
                                            color: #667066;
                                        "
                                    >
                                        Przygotuj przedmiot i wyślij go
                                        do nowego właściciela.
                                    </td>
                                </tr>
                            </table>
                        </div>

                        <!-- CTA -->
                        <div style="
                            margin-top: 30px;
                            text-align: center;
                        ">
                            <a
                                href="https://kasoa.pl"
                                style="
                                    display: inline-block;
                                    padding: 14px 28px;
                                    background-color: #68764B;
                                    color: #FFFFFF;
                                    text-decoration: none;
                                    border-radius: 9px;
                                    font-size: 13px;
                                    font-weight: 700;
                                "
                            >
                                Przejdź do kasoa.pl →
                            </a>
                        </div>
                    </td>
                </tr>

                <!-- Footer -->
                <tr>
                    <td
                        style="
                            background-color: #182016;
                            padding: 24px 30px;
                            border-radius: 0 0 16px 16px;
                            text-align: center;
                        "
                    >
                        <div style="
                            font-size: 17px;
                            font-weight: 800;
                            color: #FFFFFF;
                        ">
                            kasoa<span style="
                                font-weight: 400;
                                color: #AEB69D;
                            ">.pl</span>
                        </div>

                        <div style="
                            margin-top: 7px;
                            font-size: 11px;
                            color: #AEB69D;
                        ">
                            Good things. A second chance.
                        </div>

                        <div style="
                            margin-top: 14px;
                            font-size: 10px;
                            line-height: 1.5;
                            color: #7F8878;
                        ">
                            Ta wiadomość została wygenerowana automatycznie.
                            <br>
                            Zarządzaj swoimi produktami w zakładce „Moje produkty”.
                        </div>
                    </td>
                </tr>
            </table>
        </div>
        """.formatted(
                escapeHtml(sellerName),
                escapeHtml(productName),
                price != null ? price.toPlainString() : "0.00"
        );

        // Executes using the background async thread pool
        sendHtmlEmail(sellerEmail, subject, htmlContent);
    }



    /**
     * Sends a transactional confirmation email to the Buyer with Escrow protection updates.
     */
    @Async
    public void sendOrderConfirmationToBuyer(String buyerEmail, String buyerName, String orderNumber, String productName, java.math.BigDecimal totalAmount) {
        String subject = "Twoje zamówienie na Kasoa.pl zostało opłacone! 🎉 #" + orderNumber;

        String htmlContent = """
        <div style="font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; color: #0D1313; background-color: #FFFFFF; padding: 20px; border: 1px solid #E0E7E6; border-radius: 12px;">
            <div style="padding-bottom: 20px; border-bottom: 1px solid #E0E7E6;">
                <h1 style="font-size: 24px; font-weight: 800; color: #1E3A3A; margin: 0; letter-spacing: -0.03em;">Kasoa<span style="font-weight: 400; color: #5E6C6A;">.pl</span></h1>
            </div>
            <div style="padding: 20px 0;">
                <h2 style="font-size: 18px; font-weight: 700; margin: 0 0 12px 0;">Dziękujemy za zakupy, %s!</h2>
                <p style="font-size: 14px; line-height: 1.6; color: #42504F; margin: 0 0 20px 0;">
                    Twoja płatność za zamówienie <strong>#%s</strong> została pomyślnie przetworzona. 
                    Środki zostały bezpiecznie ulokowane w **systemie ochrony kupujących (Escrow)** Kasoa.pl.
                </p>
                
                <div style="background-color: #F4F7F6; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
                    <span style="font-size: 11px; font-weight: 700; color: #5E6C6A; display: block; text-transform: uppercase; letter-spacing: 0.5px;">Podsumowanie zakupu</span>
                    <strong style="font-size: 14px; color: #0D1313; display: block; margin-top: 4px;">%s</strong>
                    <strong style="font-size: 16px; color: #1E3A3A; display: block; margin-top: 4px;">Kwota: %s zł</strong>
                </div>

                <div style="background-color: #EDF7F3; border-radius: 8px; padding: 15px; margin-bottom: 20px;">
                    <strong style="font-size: 13px; color: #2A6B4E; display: block; margin-bottom: 4px;">🛡️ Bezpieczna Transakcja Escrow:</strong>
                    <p style="font-size: 13px; line-height: 1.4; color: #2A6B4E; margin: 0;">
                        Sprzedawca nie otrzyma Twoich pieniędzy, dopóki nie odbierzesz paczki i nie potwierdzisz w swoim panelu, że produkt jest zgodny z opisem.
                    </p>
                </div>
                
                <p style="font-size: 13px; color: #5E6C6A; margin: 0;">
                    Powiadomiliśmy już sprzedawcę o konieczności przygotowania przesyłki. Wyślemy Ci kolejny e-mail z linkiem do śledzenia paczki, gdy tylko ruszy w drogę!
                </p>
            </div>
            <div style="border-top: 1px solid #E0E7E6; padding-top: 20px; text-align: center; font-size: 11px; color: #5E6C6A;">
                Wiadomość wygenerowana automatycznie przez Kasoa.pl. Status zamówienia możesz śledzić w zakładce 'Zamówienia'.
            </div>
        </div>
        """.formatted(buyerName, orderNumber, productName, totalAmount.toString());

        sendHtmlEmail(buyerEmail, subject, htmlContent);
    }

    /**
     * Sends a notification email to the Seller instructing them to pack and ship the item.
     */
    @Async
    public void sendOrderActionToSeller(String sellerEmail, String sellerName, String orderNumber, String productName, java.math.BigDecimal payoutAmount) {
        String subject = "Twój przedmiot został sprzedany! 🚀 Przygotuj wysyłkę zamówienia #" + orderNumber;

        String htmlContent = """
        <div style="font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; color: #0D1313; background-color: #FFFFFF; padding: 20px; border: 1px solid #E0E7E6; border-radius: 12px;">
            <div style="padding-bottom: 20px; border-bottom: 1px solid #E0E7E6;">
                <h1 style="font-size: 24px; font-weight: 800; color: #1E3A3A; margin: 0; letter-spacing: -0.03em;">Kasoa<span style="font-weight: 400; color: #5E6C6A;">.pl</span></h1>
            </div>
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
            <div style="border-top: 1px solid #E0E7E6; padding-top: 20px; text-align: center; font-size: 11px; color: #5E6C6A;">
                Wiadomość wygenerowana automatycznie przez Kasoa.pl. Dziękujemy za budowanie z nami ekologicznego obiegu ubrań!
            </div>
        </div>
        """.formatted(sellerName, orderNumber, productName, payoutAmount.toString());

        sendHtmlEmail(sellerEmail, subject, htmlContent);
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
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport"
                  content="width=device-width, initial-scale=1.0">
            <title>Nowe zamówienie</title>
        </head>

        <body style="
            margin:0;
            padding:0;
            background:#F5F6F1;
            font-family:Arial,Helvetica,sans-serif;
            color:#182016;
        ">

        <table width="100%%"
               cellpadding="0"
               cellspacing="0"
               border="0"
               style="background:#F5F6F1;">

            <tr>
                <td align="center"
                    style="padding:32px 16px;">

                    <table width="100%%"
                           cellpadding="0"
                           cellspacing="0"
                           border="0"
                           style="
                               max-width:620px;
                               background:#FFFFFF;
                               border-radius:18px;
                               overflow:hidden;
                           ">

                        <!-- HEADER -->

                        <tr>
                            <td style="
                                padding:28px 32px;
                                background:#68764B;
                            ">

                                <div style="
                                    font-size:26px;
                                    line-height:32px;
                                    font-weight:800;
                                    color:#FFFFFF;
                                ">
                                    kasoa.pl
                                </div>

                                <div style="
                                    margin-top:5px;
                                    font-size:13px;
                                    line-height:20px;
                                    color:#EEF1E8;
                                ">
                                    Druga ręka. Dobre rzeczy. Mniej odpadów.
                                </div>

                            </td>
                        </tr>


                        <!-- CONTENT -->

                        <tr>
                            <td style="
                                padding:32px;
                            ">

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


                                <!-- ORDER NUMBER -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:24px;
                                           background:#EEF1E8;
                                           border-radius:12px;
                                       ">

                                    <tr>
                                        <td style="
                                            padding:16px 18px;
                                        ">

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

                                        </td>
                                    </tr>

                                </table>


                                <!-- PRODUCTS -->

                                <div style="
                                    margin-top:30px;
                                    font-size:18px;
                                    line-height:24px;
                                    font-weight:800;
                                    color:#182016;
                                ">
                                    Produkty
                                </div>

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:8px;
                                           border-collapse:collapse;
                                       ">

                                    %s

                                </table>


                                <!-- ORDER TOTAL -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:16px;
                                           border-collapse:collapse;
                                       ">

                                    <tr>
                                        <td style="
                                            padding:6px 0;
                                            font-size:14px;
                                            color:#687064;
                                        ">
                                            Produkty
                                        </td>

                                        <td style="
                                            padding:6px 0;
                                            text-align:right;
                                            font-size:14px;
                                            color:#182016;
                                            font-weight:600;
                                        ">
                                            %s PLN
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="
                                            padding:6px 0;
                                            font-size:14px;
                                            color:#687064;
                                        ">
                                            Dostawa
                                        </td>

                                        <td style="
                                            padding:6px 0;
                                            text-align:right;
                                            font-size:14px;
                                            color:#182016;
                                            font-weight:600;
                                        ">
                                            %s PLN
                                        </td>
                                    </tr>

                                    <tr>
                                        <td colspan="2"
                                            style="
                                                padding-top:12px;
                                                border-top:1px solid #DDE1D7;
                                            ">
                                        </td>
                                    </tr>

                                    <tr>
                                        <td style="
                                            padding:8px 0;
                                            font-size:17px;
                                            font-weight:800;
                                            color:#182016;
                                        ">
                                            Razem
                                        </td>

                                        <td style="
                                            padding:8px 0;
                                            text-align:right;
                                            font-size:18px;
                                            font-weight:800;
                                            color:#68764B;
                                        ">
                                            %s PLN
                                        </td>
                                    </tr>

                                </table>


                                <!-- SHIPPING ADDRESS -->

                                <div style="
                                    margin-top:32px;
                                    font-size:18px;
                                    line-height:24px;
                                    font-weight:800;
                                    color:#182016;
                                ">
                                    Dane do przesyłki
                                </div>

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:12px;
                                           background:#F7F8F4;
                                           border-radius:12px;
                                       ">

                                    <tr>
                                        <td style="
                                            padding:18px;
                                        ">

                                            <table width="100%%"
                                                   cellpadding="0"
                                                   cellspacing="0"
                                                   border="0">

                                                %s

                                            </table>

                                            <div style="
                                                margin:16px 0;
                                                border-top:1px solid #E0E4DB;
                                                height:1px;
                                                line-height:1px;
                                            ">
                                                &nbsp;
                                            </div>

                                            <table width="100%%"
                                                   cellpadding="0"
                                                   cellspacing="0"
                                                   border="0">

                                                %s

                                            </table>

                                        </td>
                                    </tr>

                                </table>


                                <!-- SELLER INSTRUCTIONS -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:24px;
                                           background:#EEF1E8;
                                           border-radius:12px;
                                       ">

                                    <tr>
                                        <td style="
                                            padding:20px;
                                        ">

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
                                                <br><br>
                                                2. Zapakuj je bezpiecznie,
                                                aby dotarły w dobrym stanie.
                                                <br><br>
                                                3. Wyślij przesyłkę na podany
                                                adres odbiorcy.
                                                <br><br>
                                                4. Po nadaniu przesyłki
                                                zaktualizuj status wysyłki
                                                w Kasoa.pl.
                                                <br><br>
                                                5. Zachowaj potwierdzenie
                                                nadania przesyłki.
                                            </div>

                                        </td>
                                    </tr>

                                </table>


                                <!-- PAYOUT -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin-top:20px;
                                           background:#F7F8F4;
                                           border:1px solid #E0E4DB;
                                           border-radius:12px;
                                       ">

                                    <tr>
                                        <td style="
                                            padding:20px;
                                        ">

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

                                        </td>
                                    </tr>

                                </table>


                                <!-- CTA -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="margin-top:28px;">

                                    <tr>
                                        <td align="center">

                                            <a href="https://kasoa.pl"
                                               style="
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

                                        </td>
                                    </tr>

                                </table>

                            </td>
                        </tr>


                        <!-- FOOTER -->

                        <tr>
                            <td style="
                                padding:24px 32px;
                                background:#F7F8F4;
                                border-top:1px solid #E5E8E0;
                            ">

                                <div style="
                                    font-size:13px;
                                    line-height:20px;
                                    color:#687064;
                                    text-align:center;
                                ">
                                    Ta wiadomość została wysłana
                                    automatycznie przez Kasoa.pl.
                                </div>

                                <div style="
                                    margin-top:6px;
                                    font-size:12px;
                                    line-height:18px;
                                    color:#8A9184;
                                    text-align:center;
                                ">
                                    © Kasoa.pl — Druga ręka. Dobre rzeczy.
                                </div>

                            </td>
                        </tr>

                    </table>

                </td>
            </tr>

        </table>

        </body>
        </html>
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
                html
        );
    }
    /**
     * Sends a dynamic shipment tracking notification to the Buyer for the Kasoa MVP.
     * Automatically adapts the subject and message header box to match any lifecycle status.
     */

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

        String safeBuyerName = escapeHtml(buyerName);
        String safeOrderNumber = escapeHtml(orderNumber);
        String safeProductName = escapeHtml(productName);
        String safeShipmentStatus = escapeHtml(shipmentStatus);
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
                    "https://kasoa.pl/orders/"
                            + URLEncoder.encode(
                            orderNumber,
                            StandardCharsets.UTF_8
                    )
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
        <!DOCTYPE html>
        <html>

        <head>
            <meta charset="UTF-8">
            <meta name="viewport"
                  content="width=device-width, initial-scale=1.0">

            <title>
                Aktualizacja zamówienia - kasoa.pl
            </title>
        </head>


        <body style="
            margin:0;
            padding:0;
            background:#F5F6F1;
            font-family:Arial, Helvetica, sans-serif;
            color:#182016;
        ">

        <table width="100%%"
               cellpadding="0"
               cellspacing="0"
               border="0"
               style="
                   width:100%%;
                   background:#F5F6F1;
                   padding:32px 16px;
               ">

            <tr>
                <td align="center">

                    <table width="100%%"
                           cellpadding="0"
                           cellspacing="0"
                           border="0"
                           style="
                               width:100%%;
                               max-width:640px;
                               background:#FFFFFF;
                               border-radius:18px;
                               overflow:hidden;
                           ">


                        <!-- ================================================= -->
                        <!-- HEADER -->
                        <!-- ================================================= -->

                        <tr>
                            <td style="
                                background:#68764B;
                                padding:28px 32px;
                                text-align:center;
                            ">

                                <div style="
                                    font-size:28px;
                                    line-height:34px;
                                    font-weight:800;
                                    letter-spacing:-0.5px;
                                    color:#FFFFFF;
                                ">
                                    kasoa.pl
                                </div>

                                <div style="
                                    margin-top:6px;
                                    font-size:13px;
                                    line-height:20px;
                                    color:#E9EDDF;
                                ">
                                    Druga ręka. Dobre rzeczy. Mniej odpadów.
                                </div>

                            </td>
                        </tr>


                        <!-- ================================================= -->
                        <!-- CONTENT -->
                        <!-- ================================================= -->

                        <tr>
                            <td style="
                                padding:36px 32px 32px 32px;
                            ">


                                <!-- GREETING -->

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


                                <!-- ================================================= -->
                                <!-- STATUS -->
                                <!-- ================================================= -->

                                <table cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
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


                                <!-- ================================================= -->
                                <!-- ORDER CARD -->
                                <!-- ================================================= -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           width:100%%;
                                           background:#F8F9F5;
                                           border:1px solid #E4E7DE;
                                           border-radius:14px;
                                           margin:0 0 20px 0;
                                       ">

                                    <tr>

                                        <td style="
                                            padding:22px;
                                        ">

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

                                        </td>

                                    </tr>

                                </table>


                                <!-- ================================================= -->
                                <!-- SHIPPING CARD -->
                                <!-- ================================================= -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           width:100%%;
                                           background:#FFFFFF;
                                           border:1px solid #E4E7DE;
                                           border-radius:14px;
                                           margin:0 0 24px 0;
                                       ">

                                    <tr>

                                        <td style="
                                            padding:22px;
                                        ">

                                            <div style="
                                                font-size:17px;
                                                line-height:24px;
                                                font-weight:700;
                                                color:#182016;
                                                margin-bottom:20px;
                                            ">
                                                📦 Informacje o przesyłce
                                            </div>

                                            <table width="100%%"
                                                   cellpadding="0"
                                                   cellspacing="0"
                                                   border="0">

                                                %s
                                                %s

                                            </table>

                                        </td>

                                    </tr>

                                </table>


                                <!-- ================================================= -->
                                <!-- RECEIPT CONFIRMATION -->
                                <!-- ================================================= -->

                                %s


                                <!-- ================================================= -->
                                <!-- ESCROW -->
                                <!-- ================================================= -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           width:100%%;
                                           background:#F8F9F5;
                                           border:1px solid #E4E7DE;
                                           border-radius:14px;
                                           margin:0 0 24px 0;
                                       ">

                                    <tr>

                                        <td style="
                                            padding:22px;
                                        ">

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

                                        </td>

                                    </tr>

                                </table>


                                <!-- ================================================= -->
                                <!-- NEXT STEP -->
                                <!-- ================================================= -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           width:100%%;
                                           background:#EEF1E8;
                                           border-radius:14px;
                                           margin:0 0 28px 0;
                                       ">

                                    <tr>

                                        <td style="
                                            padding:22px;
                                        ">

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

                                        </td>

                                    </tr>

                                </table>


                                <!-- ================================================= -->
                                <!-- CTA -->
                                <!-- ================================================= -->

                                <table width="100%%"
                                       cellpadding="0"
                                       cellspacing="0"
                                       border="0"
                                       style="
                                           margin:0 0 8px 0;
                                       ">

                                    <tr>

                                        <td align="center">

                                            <a href="https://kasoa.pl"
                                               style="
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

                                        </td>

                                    </tr>

                                </table>

                            </td>
                        </tr>


                        <!-- ================================================= -->
                        <!-- FOOTER -->
                        <!-- ================================================= -->

                        <tr>

                            <td style="
                                background:#F8F9F5;
                                border-top:1px solid #E4E7DE;
                                padding:24px 32px;
                                text-align:center;
                            ">

                                <div style="
                                    font-size:15px;
                                    line-height:22px;
                                    font-weight:700;
                                    color:#182016;
                                    margin-bottom:7px;
                                ">
                                    kasoa.pl
                                </div>

                                <div style="
                                    font-size:12px;
                                    line-height:19px;
                                    color:#7A8172;
                                ">
                                    Dziękujemy, że dajesz rzeczom drugą szansę.
                                </div>

                                <div style="
                                    font-size:11px;
                                    line-height:18px;
                                    color:#9AA092;
                                    margin-top:12px;
                                ">
                                    Ta wiadomość została wygenerowana automatycznie.
                                </div>

                            </td>

                        </tr>

                    </table>

                </td>
            </tr>

        </table>

        </body>
        </html>
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
                html
        );
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

}
