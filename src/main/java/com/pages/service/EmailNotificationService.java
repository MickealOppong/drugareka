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


    // =====================================================================
    //  DESIGN TOKENS
    //  One brand colour, one neutral scale. Status colours (warn / danger)
    //  are used only for badges and notice panels, never for buttons.
    // =====================================================================

    private static final String FONT =
            "-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,Helvetica Neue,Arial,sans-serif";
    private static final String MONO =
            "SFMono-Regular,Menlo,Consolas,Liberation Mono,monospace";

    private static final String INK = "#1A1F16";     // headings, values
    private static final String BODY = "#3D4538";    // paragraphs
    private static final String MUTED = "#677060";   // secondary text
    private static final String FAINT = "#8F968A";   // labels, captions
    private static final String LINE = "#E8EAE3";    // hairlines
    private static final String BRAND = "#66704A";   // the only button colour

    private static final String HOME_URL = "https://www.kasoa.pl";

    private enum Tone {
        NEUTRAL("#F7F7F4", "#E8EAE3", "#66704A"),
        /** Anything the recipient must notice: deadlines, required actions, cancellations, failures. */
        ALERT("#FDF1EF", "#F0CFCA", "#B3261E");

        final String bg;
        final String line;
        final String fg;

        Tone(String bg, String line, String fg) {
            this.bg = bg;
            this.line = line;
            this.fg = fg;
        }
    }


    // =====================================================================
    //  LAYOUT SHELL
    // =====================================================================

    /**
     * Shared Kasoa email shell: brand header, white content card, dark footer.
     * The marker attribute lets sendHtmlEmail() detect an already wrapped message.
     */
    private String buildEmailLayout(String bodyContent) {
        return """
        <!DOCTYPE html>
        <html lang="pl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <meta http-equiv="X-UA-Compatible" content="IE=edge">
            <meta name="color-scheme" content="light">
            <meta name="supported-color-schemes" content="light">
            <title>kasoa.pl</title>
            <style>
                body { margin:0; padding:0; width:100%; background-color:#F4F5F1;
                       -webkit-text-size-adjust:100%; -ms-text-size-adjust:100%; }
                table { border-collapse:collapse; mso-table-lspace:0; mso-table-rspace:0; }
                img { border:0; max-width:100%; height:auto; }
                a { color:#66704A; }
                @media only screen and (max-width:620px) {
                    .k-outer { padding:16px 10px 0 !important; }
                    .k-pad { padding-left:22px !important; padding-right:22px !important; }
                    .k-h1 { font-size:23px !important; line-height:29px !important; }
                    .k-btn-wrap { width:100% !important; }
                    .k-btn { display:block !important; }
                }
            </style>
        </head>
        <body style="margin:0;padding:0;background-color:#F4F5F1;font-family:__FONT__;color:#1A1F16;">
            <table data-kasoa-email-layout="true" role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"
                   style="width:100%;background-color:#F4F5F1;">
                <tr>
                    <td class="k-outer" align="center" style="padding:32px 12px 0;">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                               style="width:100%;max-width:600px;border-collapse:separate;background-color:#FFFFFF;border:1px solid #E8EAE3;border-radius:16px;">
                            <tr>
                                <td class="k-pad" align="center" bgcolor="#66704A"
                                    style="padding:26px 36px;background-color:#66704A;border-radius:16px 16px 0 0;text-align:center;">
                                    <a href="https://www.kasoa.pl"
                                       style="display:inline-block;font-family:__FONT__;font-size:28px;line-height:34px;font-weight:800;letter-spacing:-0.5px;color:#FFFFFF;text-decoration:none;">kasoa.pl</a>
                                    <div style="margin-top:4px;font-family:__FONT__;font-size:12px;line-height:18px;font-weight:600;letter-spacing:0.6px;color:#E6EAD8;">
                                        Dobre rzeczy. Drugie życie.
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td class="k-pad" style="padding:36px 36px 40px;background-color:#FFFFFF;font-family:__FONT__;font-size:15px;line-height:24px;color:#3D4538;">
                                    __BODY__
                                </td>
                            </tr>
                            <tr>
                                <td class="k-pad" align="center" bgcolor="#2B2B2B"
                                    style="padding:28px 36px 30px;background-color:#2B2B2B;border-radius:0 0 16px 16px;text-align:center;">
                                    <div style="font-family:__FONT__;font-size:17px;line-height:24px;font-weight:800;color:#FFFFFF;">
                                        kasoa<span style="font-weight:500;color:#B9C49A;">.pl</span>
                                    </div>
                                    <div style="margin-top:4px;font-family:__FONT__;font-size:12px;line-height:18px;color:#CFCFCA;">
                                        Dobre rzeczy. Drugie życie. (Good things. A second chance.)
                                    </div>
                                    <div style="margin-top:16px;padding-top:16px;border-top:1px solid #444444;font-family:__FONT__;font-size:11px;line-height:18px;color:#9C9C97;">
                                        Ta wiadomość została wygenerowana automatycznie.<br><br>
                                        KASAWA Sp. z o.o. · ul. Kostromska nr 55 lok. 95, 97-300 Piotrków Trybunalski, Polska<br>
                                        KRS: 0001269876 · NIP: 7712946929
                                    </div>
                                </td>
                            </tr>
                        </table>
                        <div style="height:24px;line-height:24px;font-size:1px;">&nbsp;</div>
                    </td>
                </tr>
            </table>
        </body>
        </html>
        """
                .replace("__FONT__", FONT)
                .replace("__BODY__", bodyContent);
    }


    // =====================================================================
    //  COMPONENT HELPERS
    //  All helpers take already-escaped HTML. Only button()/textLink()
    //  escape their URL argument themselves.
    // =====================================================================

    private String el(String tag, String style, String inner) {
        return "<" + tag + " style=\"" + style + "\">" + inner + "</" + tag + ">";
    }

    private String h1(String text) {
        return "<h1 class=\"k-h1\" style=\"margin:0 0 12px;font-family:" + FONT
                + ";font-size:26px;line-height:32px;font-weight:800;letter-spacing:-0.5px;color:" + INK + ";\">"
                + text + "</h1>";
    }

    /** Heading with a muted English subtitle. */
    private String h1(String pl, String en) {
        return "<h1 class=\"k-h1\" style=\"margin:0 0 4px;font-family:" + FONT
                + ";font-size:26px;line-height:32px;font-weight:800;letter-spacing:-0.5px;color:" + INK + ";\">"
                + pl + "</h1>"
                + el("div", "margin:0 0 20px;font-size:14px;line-height:20px;color:" + FAINT + ";", en);
    }

    private String h2(String text) {
        return el("h2", "margin:0 0 10px;font-family:" + FONT
                + ";font-size:17px;line-height:24px;font-weight:700;color:" + INK + ";", text);
    }

    /** Intro paragraph under a heading. */
    private String lead(String html) {
        return el("p", "margin:0 0 24px;font-size:15px;line-height:24px;color:" + MUTED + ";", html);
    }

    private String p(String html) {
        return el("p", "margin:0 0 16px;font-size:15px;line-height:24px;color:" + BODY + ";", html);
    }

    private String muted(String html) {
        return el("p", "margin:0;font-size:13px;line-height:21px;color:" + MUTED + ";", html);
    }

    private String caption(String html) {
        return el("p", "margin:14px 0 0;font-size:12px;line-height:19px;color:" + FAINT + ";text-align:center;", html);
    }

    private String strong(String html) {
        return "<strong style=\"color:" + INK + ";\">" + html + "</strong>";
    }

    /** Polish text followed by a muted English translation. */
    private String bi(String pl, String en) {
        return pl + "<br><span style=\"color:" + FAINT + ";\">" + en + "</span>";
    }

    private String label(String text) {
        return el("div", "margin:0 0 6px;font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.8px;"
                + "text-transform:uppercase;color:" + FAINT + ";", text);
    }

    private String value(String html) {
        return el("div", "margin:0 0 16px;font-size:16px;line-height:24px;font-weight:700;color:" + INK
                + ";overflow-wrap:anywhere;", html);
    }

    private String orderNumber(String safeOrderNumber) {
        return el("div", "margin:0;font-family:" + MONO + ";font-size:18px;line-height:26px;font-weight:700;color:"
                + INK + ";overflow-wrap:anywhere;", "#" + safeOrderNumber);
    }

    private String spacer(int px) {
        return "<div style=\"height:" + px + "px;line-height:" + px + "px;font-size:1px;\">&nbsp;</div>";
    }

    private String divider() {
        return "<div style=\"margin:20px 0;height:1px;line-height:1px;font-size:1px;background-color:" + LINE + ";\">&nbsp;</div>";
    }

    private String badge(String text, Tone tone) {
        return "<div style=\"margin:0 0 18px;\"><span style=\"display:inline-block;padding:6px 12px;"
                + "border-radius:999px;background-color:" + tone.bg + ";border:1px solid " + tone.line
                + ";font-family:" + FONT + ";font-size:12px;line-height:16px;font-weight:700;letter-spacing:0.3px;color:"
                + tone.fg + ";\">" + text + "</span></div>";
    }

    private String panel(Tone tone, String inner) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"width:100%;margin:0 0 20px;border-collapse:separate;\"><tr>"
                + "<td style=\"padding:18px 20px;background-color:" + tone.bg + ";border:1px solid " + tone.line
                + (tone == Tone.ALERT ? ";border-left:4px solid " + tone.fg : "")
                + ";border-radius:12px;font-family:" + FONT + ";font-size:14px;line-height:22px;color:" + BODY + ";\">"
                + inner + "</td></tr></table>";
    }

    /** Panel with a brand accent on the left, used to quote user-written text. */
    private String quotePanel(String labelText, String safeBody) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"width:100%;margin:0 0 22px;border-collapse:separate;\"><tr>"
                + "<td style=\"padding:16px 20px;background-color:#F7F7F4;border:1px solid " + LINE
                + ";border-left:3px solid " + BRAND + ";border-radius:10px;font-family:" + FONT + ";\">"
                + label(labelText)
                + el("div", "font-size:14px;line-height:23px;color:" + BODY + ";overflow-wrap:anywhere;", safeBody)
                + "</td></tr></table>";
    }

    private String section(String title, String inner) {
        return "<div style=\"margin:28px 0 0;padding-top:24px;border-top:1px solid " + LINE + ";\">"
                + h2(title) + inner + "</div>";
    }

    private String steps(String... items) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            boolean last = i == items.length - 1;
            rows.append("<tr><td width=\"38\" valign=\"top\" style=\"padding:0 0 ").append(last ? 0 : 14)
                    .append("px;\"><div style=\"width:26px;height:26px;line-height:26px;text-align:center;border-radius:13px;"
                            + "background-color:").append(BRAND)
                    .append(";color:#FFFFFF;font-family:").append(FONT)
                    .append(";font-size:12px;font-weight:700;\">").append(i + 1).append("</div></td>")
                    .append("<td valign=\"top\" style=\"padding:2px 0 ").append(last ? 0 : 14)
                    .append("px;font-family:").append(FONT)
                    .append(";font-size:14px;line-height:22px;color:").append(BODY).append(";\">")
                    .append(items[i]).append("</td></tr>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"width:100%;\">" + rows + "</table>";
    }

    // ---- Buttons: one solid brand colour for every primary action --------

    /** PRIMARY = the one olive CTA, SECONDARY = neutral outline, DANGER = red, for negative actions. */
    private enum Variant { PRIMARY, SECONDARY, DANGER }

    private String button(String url, String text) {
        return buttonHtml(url, text, Variant.PRIMARY, false, "28px auto 0");
    }

    private String buttonFull(String url, String text, Variant variant) {
        return buttonHtml(url, text, variant, true, "0 0 12px");
    }

    private String buttonHtml(String url, String text, Variant variant, boolean full, String margin) {
        String bg = variant == Variant.PRIMARY ? BRAND : variant == Variant.DANGER ? "#FDF1EF" : "#FFFFFF";
        String fg = variant == Variant.PRIMARY ? "#FFFFFF" : variant == Variant.DANGER ? Tone.ALERT.fg : INK;
        String border = variant == Variant.PRIMARY ? BRAND : variant == Variant.DANGER ? "#E2A8A1" : "#D3D8C9";
        String tableAttrs = full
                ? "width=\"100%\" style=\"width:100%;margin:" + margin + ";border-collapse:separate;\""
                : "align=\"center\" style=\"margin:" + margin + ";border-collapse:separate;\"";
        return "<table role=\"presentation\" class=\"k-btn-wrap\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" "
                + tableAttrs + "><tr>"
                + "<td align=\"center\" bgcolor=\"" + bg + "\" style=\"background-color:" + bg + ";border:1px solid "
                + border + ";border-radius:10px;\">"
                + "<a class=\"k-btn\" href=\"" + escapeHtml(url) + "\" target=\"_blank\" style=\"display:"
                + (full ? "block" : "inline-block") + ";padding:14px 28px;font-family:" + FONT
                + ";font-size:15px;line-height:20px;font-weight:700;color:" + fg
                + ";text-decoration:none;text-align:center;border-radius:10px;\">" + text + "</a>"
                + "</td></tr></table>";
    }

    private String textLink(String url, String text) {
        return "<a href=\"" + escapeHtml(url) + "\" target=\"_blank\" style=\"font-size:14px;line-height:22px;"
                + "font-weight:700;color:" + BRAND + ";text-decoration:none;\">" + text + "</a>";
    }

    // ---- Key / value tables ----------------------------------------------

    private String table(String rows) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"width:100%;margin:0 0 4px;\">" + rows + "</table>";
    }

    private String kvRow(String label, String value) {
        return "<tr><td style=\"padding:7px 0;font-family:" + FONT + ";font-size:13px;line-height:20px;color:" + MUTED
                + ";\">" + label + "</td><td align=\"right\" style=\"padding:7px 0 7px 16px;font-family:" + FONT
                + ";font-size:14px;line-height:20px;color:" + INK + ";white-space:nowrap;\">" + value + "</td></tr>";
    }

    /** Last row of a group: closes it with a hairline. */
    private String kvRowEnd(String label, String value) {
        return "<tr><td style=\"padding:7px 0 18px;border-bottom:1px solid " + LINE + ";font-family:" + FONT
                + ";font-size:13px;line-height:20px;color:" + MUTED + ";\">" + label
                + "</td><td align=\"right\" style=\"padding:7px 0 18px 16px;border-bottom:1px solid " + LINE
                + ";font-family:" + FONT + ";font-size:14px;line-height:20px;color:" + INK + ";white-space:nowrap;\">"
                + value + "</td></tr>";
    }

    private String kvTotal(String label, String value) {
        return "<tr><td style=\"padding:14px 0 4px;font-family:" + FONT
                + ";font-size:14px;line-height:24px;font-weight:700;color:" + INK + ";\">" + label
                + "</td><td align=\"right\" style=\"padding:14px 0 4px 16px;font-family:" + FONT
                + ";font-size:18px;line-height:24px;font-weight:800;color:" + INK + ";white-space:nowrap;\">"
                + value + "</td></tr>";
    }

    private String productRow(String safeName, String qtyLine, String price) {
        return "<tr><td valign=\"top\" style=\"padding:14px 0;border-bottom:1px solid " + LINE + ";font-family:" + FONT + ";\">"
                + el("div", "font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeName)
                + el("div", "margin-top:2px;font-size:13px;line-height:20px;color:" + MUTED + ";", qtyLine)
                + "</td><td align=\"right\" valign=\"top\" style=\"padding:14px 0 14px 16px;border-bottom:1px solid " + LINE
                + ";font-family:" + FONT + ";font-size:15px;line-height:22px;font-weight:700;color:" + INK
                + ";white-space:nowrap;\">" + price + "</td></tr>";
    }

    private String infoBlock(String labelText, String safeValue) {
        return "<div style=\"margin:0 0 16px;\">" + label(labelText)
                + el("div", "font-size:15px;line-height:22px;font-weight:600;color:" + INK + ";overflow-wrap:anywhere;", safeValue)
                + "</div>";
    }


    // =====================================================================
    //  PASSWORD RESET
    // =====================================================================

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

        String htmlContent =
                h1("Resetowanie hasła", "(Reset your password)")
                        + p(bi("Otrzymaliśmy prośbę o zresetowanie hasła do Twojego konta w serwisie Kasoa.pl.",
                        "(We received a request to reset the password for your Kasoa.pl account.)"))
                        + p(bi("Kliknij poniższy przycisk, aby ustawić nowe hasło.",
                        "(Click the button below to create a new password.)"))
                        + button(resetLink, "Zresetuj hasło (Reset password)")
                        + spacer(32)
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 8px;font-size:14px;line-height:22px;color:" + BODY + ";",
                                strong("Ważne:") + " Link do resetowania hasła wygaśnie po 24 godzinach. "
                                        + "Jeśli nie prosiłeś(-aś) o zmianę hasła, zignoruj tę wiadomość. "
                                        + "Twoje hasło pozostanie bez zmian.")
                                + el("div", "font-size:13px;line-height:20px;color:" + FAINT + ";",
                                "(This password reset link expires in 24 hours. If you did not request a password reset, "
                                        + "you can ignore this email. Your password will remain unchanged.)"));

        sendHtmlEmail(email, subject, buildEmailLayout(htmlContent));
    }


    /**
     * Sends an HTML email through the Resend HTTPS API.
     *
     * This deliberately does not use SMTP, because Railway Hobby blocks
     * outbound SMTP connections. All communication with Resend is HTTPS.
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            if (htmlContent == null || htmlContent.isBlank()) {
                log.warn("Skipping Kasoa email with empty HTML content. Recipient: {}", to);
                return;
            }

            // Enforce the shared branded layout at the final dispatch boundary.
            // Individual notification methods may already wrap their content;
            // the marker prevents the header and footer from being duplicated.
            if (!htmlContent.contains("data-kasoa-email-layout=\"true\"")) {
                htmlContent = buildEmailLayout(htmlContent);
            }

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


    // =====================================================================
    //  BUYER: ORDER PAID
    // =====================================================================

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

                itemsHtml.append("<tr><td colspan=\"2\" style=\"padding:18px 0 6px;font-family:").append(FONT).append(";\">")
                        .append(el("div", "font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", productName))
                        .append(el("div", "margin-top:6px;font-size:13px;line-height:21px;color:" + MUTED + ";",
                                "Sprzedawca: " + strong(sellerName)
                                        + "<br>Status sprzedawcy: " + strong("Osoba prywatna")
                                        + "<br>Ilość: " + strong(escapeHtml(quantity))))
                        .append("</td></tr>")
                        .append(kvRow("Cena produktu", escapeHtml(amount) + " zł"))
                        .append(kvRow("Koszt dostawy", escapeHtml(shippingCost) + " zł"))
                        .append(kvRowEnd("Opłata serwisowa", escapeHtml(serviceCharge) + " zł"));
            }
        }

        String total = totalAmount != null
                ? totalAmount.toPlainString()
                : "0.00";

        String safeOrder = escapeHtml(orderNumber);

        String htmlContent =
                badge("✓ Płatność potwierdzona (Payment confirmed)", Tone.NEUTRAL)
                        + h1("Dziękujemy za zakupy, " + escapeHtml(buyerName) + "!")
                        + lead("Twoja płatność za zamówienie " + strong("#" + safeOrder)
                        + " została pomyślnie przetworzona. Poniżej znajdziesz szczegóły zamówienia, "
                        + "dane sprzedawców oraz informacje o podmiocie obsługującym transakcję.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(safeOrder))
                        + label("Podsumowanie zamówienia")
                        + table(itemsHtml + kvTotal("Łączna kwota zamówienia", escapeHtml(total) + " zł"))
                        + section("Informacje o sprzedawcach",
                        muted("Produkty w tym zamówieniu są oferowane przez sprzedawców wskazanych przy poszczególnych pozycjach. "
                                + "W przypadku sprzedawcy oznaczonego jako osoba prywatna sprzedaż odbywa się pomiędzy kupującym "
                                + "a tym sprzedawcą. Kasoa.pl nie jest sprzedawcą tych produktów wyłącznie z tytułu "
                                + "udostępniania platformy."))
                        + section("Podmiot obsługujący transakcję",
                        el("div", "margin:0 0 8px;font-size:14px;line-height:21px;font-weight:700;color:" + INK + ";",
                                "KASAWA SPÓŁKA Z OGRANICZONĄ ODPOWIEDZIALNOŚCIĄ")
                                + muted("KASAWA Sp. z o.o. prowadzi platformę Kasoa.pl i pełni funkcję podmiotu obsługującego "
                                + "transakcję. Spółka nie jest sprzedawcą produktów wymienionych w tym zamówieniu."))
                        + section("Co dalej?", steps(
                        "Sprzedawcy przygotują produkty do wysyłki.",
                        "Otrzymasz informacje o wysyłce i numerach śledzenia, gdy będą dostępne.",
                        "Śledź przesyłki i odbierz swoje zakupy."))
                        + button(HOME_URL, "Sprawdź moje zamówienie →");

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

        String htmlContent =
                badge("Anulowanie produktu", Tone.ALERT)
                        + h1("Produkt został anulowany")
                        + lead("Informujemy, że sprzedający anulował poniższy produkt z Twojego zamówienia.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(escapeHtml(orderNumber)))
                        + panel(Tone.NEUTRAL,
                        label("Anulowany produkt")
                                + el("div", "font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", escapeHtml(product))
                                + el("div", "margin-top:10px;font-size:13px;line-height:20px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "Status: Anulowano przez sprzedającego (Cancelled by seller)"))
                        + panel(Tone.NEUTRAL,
                        label("Kwota zwrotu")
                                + el("div", "font-size:28px;line-height:34px;font-weight:800;color:" + INK + ";", escapeHtml(formattedAmount) + " zł"))
                        + muted("Przepraszamy za niedogodności. Zwrot środków zostanie zlecony na pierwotną metodę płatności.")
                        + spacer(12)
                        + muted("Pozostałe produkty z Twojego zamówienia nie są objęte tym anulowaniem. "
                        + "Szczegóły możesz sprawdzić w zakładce „Zamówienia” na swoim koncie Kasoa.")
                        + button(HOME_URL, "Sprawdź moje zamówienie →");

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

        String htmlContent =
                badge("✓ Odbiór osobisty potwierdzony (Personal collection confirmed)", Tone.NEUTRAL)
                        + h1("Odbiór osobisty został potwierdzony, " + safeBuyerName + "!")
                        + lead("Sprzedawca potwierdził Twoje zamówienie i przekazał szczegóły dotyczące odbioru. "
                        + "Możesz teraz skontaktować się ze sprzedawcą, aby ustalić dogodny termin.")
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 6px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "⏰ Masz 48 godzin na odbiór")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Od momentu otrzymania tej wiadomości masz 48 godzin, aby skontaktować się ze sprzedawcą, "
                                        + "ustalić dogodny termin i odebrać zamówiony produkt."))
                        + panel(Tone.NEUTRAL,
                        label("Szczegóły zamówienia")
                                + el("div", "margin:0 0 4px;font-size:13px;color:" + MUTED + ";", "Numer zamówienia")
                                + el("div", "margin:0 0 14px;font-family:" + MONO + ";font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";", "#" + safeOrderNumber)
                                + el("div", "margin:0 0 4px;font-size:13px;color:" + MUTED + ";", "Produkt")
                                + el("div", "font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProduct))
                        + panel(Tone.NEUTRAL,
                        label("📍 Adres odbioru")
                                + el("div", "font-size:16px;line-height:26px;font-weight:700;color:" + INK + ";", safeAddress))
                        + panel(Tone.NEUTRAL,
                        label("Kontakt ze sprzedawcą")
                                + el("div", "font-size:14px;line-height:23px;color:" + INK + ";", safeSellerContact))
                        + panel(Tone.NEUTRAL,
                        label("Informacje od sprzedawcy")
                                + el("div", "font-size:13px;line-height:21px;color:" + MUTED + ";", safeSellerComment))
                        + section("Co teraz zrobić?", steps(
                        "Skontaktuj się ze sprzedawcą, aby ustalić dogodny termin odbioru.",
                        "Odbierz produkt pod wskazanym adresem w ciągu 48 godzin od otrzymania tej wiadomości.",
                        "Po odbiorze produktu sprzedawca zaktualizuje status zamówienia. Następnie otrzymasz od Kasoa.pl "
                                + "osobną wiadomość e-mail z instrukcją potwierdzenia odbioru."))
                        + button("https://www.kasoa.pl/account/orders", "Zobacz moje zamówienia →");

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

        String htmlContent =
                badge("ODBIÓR OSOBISTY", Tone.NEUTRAL)
                        + h1("Produkt został przekazany kupującemu, " + safeSellerName + "?")
                        + lead("Po osobistym przekazaniu produktu kupującemu pamiętaj o zaktualizowaniu statusu zamówienia "
                        + "na Kasoa.pl. Dzięki temu kupujący otrzyma wiadomość e-mail z prośbą o potwierdzenie odbioru.")
                        + panel(Tone.NEUTRAL,
                        label("Szczegóły zamówienia")
                                + el("div", "margin:0 0 4px;font-size:13px;color:" + MUTED + ";", "Numer zamówienia")
                                + el("div", "margin:0 0 14px;font-family:" + MONO + ";font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";", "#" + safeOrderNumber)
                                + el("div", "margin:0 0 4px;font-size:13px;color:" + MUTED + ";", "Produkt")
                                + el("div", "font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProduct))
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 6px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";", "Wymagane działanie")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Zaloguj się na swoje konto Kasoa.pl i przejdź do szczegółów zamówienia. "
                                        + "W sekcji wysyłki zaktualizuj status zamówienia na " + strong("„Dostarczono” (DELIVERED)") + "."))
                        + section("Co stanie się dalej?", steps(
                        "Po zmianie statusu na „Dostarczono” kupujący otrzyma od Kasoa.pl osobną wiadomość e-mail "
                                + "z bezpiecznym linkiem do potwierdzenia odbioru.",
                        "Kupujący musi skorzystać z otrzymanego linku i potwierdzić, że odebrał produkt.",
                        "Po potwierdzeniu odbioru przez kupującego Kasoa.pl rozpocznie proces realizacji wypłaty należnych Ci środków."))
                        + spacer(24)
                        + muted(strong("Ważne:") + " Status „Dostarczono” aktualizuj wyłącznie po faktycznym przekazaniu produktu "
                        + "kupującemu. Sama zmiana statusu nie oznacza jeszcze potwierdzenia odbioru przez kupującego.")
                        + button("https://www.kasoa.pl/account/orders", "Przejdź do moich zamówień →");

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

        String htmlContent =
                badge("ZAMÓWIENIE ANULOWANE", Tone.ALERT)
                        + h1("Zamówienie zostało anulowane")
                        + lead("Dzień dobry " + safeBuyerName + ",<br><br>informujemy, że zamówienie "
                        + strong("#" + safeOrderNumber)
                        + " zostało anulowane, ponieważ sprzedawca nie potwierdził go w wymaganym terminie.")
                        + panel(Tone.NEUTRAL,
                        label("Anulowany produkt")
                                + el("div", "margin:0 0 18px;font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProductName)
                                + label("Kwota zwrotu")
                                + el("div", "font-size:28px;line-height:34px;font-weight:800;color:" + INK + ";", escapeHtml(total) + " zł"))
                        + muted("Zwrot zostanie przekazany na pierwotną metodę płatności. Zaksięgowanie środków może potrwać "
                        + "kilka dni roboczych, w zależności od banku lub operatora płatności.")
                        + spacer(16)
                        + muted("Przepraszamy za niedogodności i dziękujemy za wyrozumiałość. "
                        + "W razie pytań możesz skontaktować się z naszym zespołem.")
                        + button(HOME_URL, "Odwiedź Kasoa.pl →");

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }


    // =====================================================================
    //  SELLER: LISTING + DAC7
    // =====================================================================

    /**
     * Compiles a brand confirmation email when a product goes live.
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

        String htmlContent =
                badge("✓ Oferta opublikowana", Tone.NEUTRAL)
                        + h1("Cześć " + safeSellerName + "!")
                        + lead("Twój przedmiot został pomyślnie wystawiony na " + strong("kasoa.pl")
                        + ". Teraz może znaleźć nowego właściciela.")
                        + panel(Tone.NEUTRAL,
                        label("Twój przedmiot")
                                + el("div", "font-size:17px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProductName)
                                + el("div", "margin-top:10px;font-size:24px;line-height:30px;font-weight:800;color:" + INK + ";", escapeHtml(formattedPrice) + " zł"))
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 4px;font-size:14px;line-height:21px;font-weight:700;color:" + INK + ";", "💡 Wskazówka od kasoa.pl")
                                + el("div", "font-size:13px;line-height:21px;color:" + MUTED + ";",
                                "Dobre zdjęcia i dokładny opis zwiększają szanse na szybką sprzedaż."))
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 6px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "💳 Przygotuj się na otrzymywanie wypłat")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Aby móc otrzymywać płatności za sprzedane przedmioty, ukończ weryfikację sprzedawcy, "
                                        + "jeśli jeszcze tego nie zrobiłeś. Przejdź do "
                                        + strong("Konto → Profil → Weryfikacja sprzedawcy") + " i postępuj zgodnie z instrukcjami.")
                                + el("div", "margin-top:8px;font-size:12px;line-height:19px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "Warto zrobić to już teraz, aby uniknąć opóźnień w otrzymaniu wypłaty po sprzedaży."))
                        + section("Co dalej?", steps(
                        "Poczekaj na zainteresowanego kupującego.",
                        "Po sprzedaży otrzymasz od nas wszystkie informacje dotyczące wysyłki.",
                        "Przygotuj przedmiot i wyślij go do nowego właściciela."))
                        + button(HOME_URL, "Przejdź do kasoa.pl →");

        sendHtmlEmail(sellerEmail, subject, buildEmailLayout(htmlContent));
    }


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

        String htmlContent =
                h1("Cześć " + escapeHtml(firstName) + "!")
                        + p("Rozpoczął się nowy rok rozliczeniowy na " + strong("kasoa.pl")
                        + ". Twój roczny licznik sprzedaży został zresetowany.")
                        + p("Dane z poprzedniego okresu zostały zachowane:")
                        + panel(Tone.NEUTRAL, table(
                        kvRow("Sprzedaż", String.valueOf(archivedSalesCount))
                                + kvRow("Łączna wartość sprzedaży", escapeHtml(archivedGrossVolume.toPlainString()) + " PLN")))
                        + p("Licznik dla roku " + strong(String.valueOf(currentYear)) + " rozpoczyna się od zera.")
                        + p("Dziękujemy, że korzystasz z kasoa.pl.");

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

        String htmlContent =
                badge("🚨 KRYTYCZNY BŁĄD (Critical failure)", Tone.ALERT)
                        + h1("Niepowodzenie rocznego resetu DAC7", "(DAC7 annual reset failed)")
                        + p("Roczny reset roku podatkowego DAC7 dla " + strong("kasoa.pl") + " zakończył się niepowodzeniem.")
                        + panel(Tone.ALERT,
                        table(kvRow("Rok podatkowy (Tax year)", String.valueOf(currentYear)))
                                + divider()
                                + label("Błąd (Error)")
                                + el("div", "font-family:" + MONO + ";font-size:13px;line-height:20px;color:" + Tone.ALERT.fg
                                + ";overflow-wrap:anywhere;", escapeHtml(errorMessage)))
                        + p("Liczniki DAC7 mogły nie zostać zresetowane. Prosimy o natychmiastowe sprawdzenie aplikacji i bazy danych.")
                        + muted("To automatyczne powiadomienie krytyczne wygenerowane przez system podatkowy kasoa.pl.");

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

        String htmlContent =
                badge("✓ POWODZENIE (Success)", Tone.NEUTRAL)
                        + h1("Roczny reset roku podatkowego DAC7", "(DAC7 annual tax year reset)")
                        + p("Roczne przejście do nowego roku podatkowego DAC7 dla " + strong("kasoa.pl")
                        + " zostało pomyślnie zakończone.")
                        + panel(Tone.NEUTRAL, table(
                        kvRow("Rok podatkowy (Tax year)", String.valueOf(currentYear))
                                + kvRow("Zaktualizowane profile sprzedawców (Seller profiles updated)", String.valueOf(modifiedRowsCount))))
                        + p("Liczniki podatkowe DAC7 zostały zresetowane i od teraz rejestrują aktywność w nowym roku kalendarzowym.")
                        + muted("To automatyczne powiadomienie administracyjne wygenerowane przez system podatkowy kasoa.pl.");

        sendHtmlEmail(adminEmail, subject, buildEmailLayout(htmlContent));
    }


    // =====================================================================
    //  LEGACY SINGLE-PRODUCT ORDER EMAILS
    // =====================================================================

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

        String htmlContent =
                badge("✓ Płatność potwierdzona", Tone.NEUTRAL)
                        + h1("Dziękujemy za zakupy, " + escapeHtml(buyerName) + "!")
                        + lead("Twoja płatność za zamówienie " + strong("#" + escapeHtml(orderNumber))
                        + " została pomyślnie przetworzona. Poniżej znajdziesz podsumowanie zakupu oraz informacje "
                        + "o sprzedawcy i obsłudze transakcji.")
                        + panel(Tone.NEUTRAL,
                        label("Podsumowanie zamówienia")
                                + table(kvRow("Numer zamówienia", "#" + escapeHtml(orderNumber))
                                + kvRow("Produkt", escapeHtml(productName))
                                + kvRow("Sprzedawca", escapeHtml(sellerName))
                                + kvRow("Status sprzedawcy (Seller status)", "Osoba prywatna (Private seller)")
                                + kvTotal("Łączna kwota", escapeHtml(formattedAmount))))
                        + section("Informacje o sprzedawcy",
                        muted("Produkt jest sprzedawany przez wskazanego powyżej sprzedawcę będącego osobą prywatną. "
                                + "Jest to sprzedaż pomiędzy kupującym a prywatnym sprzedawcą. "
                                + "Kasoa.pl nie jest sprzedawcą tego produktu."))
                        + section("Status płatności",
                        muted("Twoja płatność została przetworzona. Sprzedawca został powiadomiony o zamówieniu i może "
                                + "przygotować przesyłkę. O kolejnych etapach realizacji zamówienia poinformujemy Cię "
                                + "w osobnej wiadomości."))
                        + spacer(24)
                        + muted("W razie pytań dotyczących zamówienia skontaktuj się z nami: "
                        + textLink("mailto:kasawa.corp@gmail.com", "kasawa.corp@gmail.com") + ".");

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(htmlContent));
    }



    /**
     * Sends a notification email to the Seller instructing them to pack and ship the item.
     */
    @Async
    public void sendOrderActionToSeller(String sellerEmail, String sellerName, String orderNumber, String productName, java.math.BigDecimal payoutAmount) {
        String subject = "Twój przedmiot został sprzedany! 🚀 Przygotuj wysyłkę zamówienia #" + orderNumber;

        String htmlContent =
                badge("🚀 SPRZEDANE", Tone.NEUTRAL)
                        + h1("Świetne wieści, " + escapeHtml(sellerName) + "!")
                        + lead("Kupujący pomyślnie opłacił Twój produkt w zamówieniu " + strong("#" + escapeHtml(orderNumber))
                        + ". Środki zostały w pełni zabezpieczone.")
                        + panel(Tone.NEUTRAL,
                        label("Sprzedany produkt")
                                + el("div", "font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", escapeHtml(productName))
                                + el("div", "margin-top:10px;font-size:14px;line-height:22px;color:" + MUTED + ";",
                                "Twój przewidywany zysk: " + el("span", "font-size:18px;font-weight:800;color:" + INK + ";",
                                        escapeHtml(payoutAmount.toString()) + " zł")))
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 10px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "📦 Co musisz teraz zrobić?")
                                + steps(
                                "Zaloguj się do swojego panelu i przejdź do sekcji " + strong("„Wysyłki”") + ".",
                                "Pobierz wygenerowaną, opłaconą etykietę przewozową.",
                                "Bezpiecznie zapakuj produkt i nadaj paczkę w ciągu najbliższych 3 dni roboczych."))
                        + muted("Pamiętaj, że środki zostaną automatycznie uwolnione do Twojego salda wypłat (Payouts) "
                        + "niezwłocznie po tym, jak kupujący potwierdzi bezpieczny odbiór paczki.");

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


    // =====================================================================
    //  SELLER: NEW ORDER (3 VARIANTS)
    // =====================================================================

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

                productsHtml.append(productRow(productName, "Quantity: " + quantity,
                        productTotal.toPlainString() + " PLN"));
            }

        } else {
            productsHtml.append("<tr><td colspan=\"2\" style=\"padding:16px 0;font-size:14px;color:" + MUTED
                    + ";\">No products available.</td></tr>");
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

        String html =
                badge("NOWE ZAMÓWIENIE", Tone.NEUTRAL)
                        + h1("Cześć " + safeSellerName + "! 👋")
                        + lead("Otrzymałeś nowe zamówienie. Przygotuj produkty do wysyłki zgodnie z poniższymi danymi.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(safeOrderNumber))
                        + h2("Produkty")
                        + table(productsHtml.toString()
                        + kvRow("Produkty", escapeHtml(productsTotal.toPlainString()) + " PLN")
                        + kvRow("Dostawa", escapeHtml(shippingTotal.toPlainString()) + " PLN")
                        + kvTotal("Razem", escapeHtml(orderTotal.toPlainString()) + " PLN"))
                        + section("Dane do przesyłki",
                        panel(Tone.NEUTRAL, table(recipientAddressHtml) + divider() + table(senderAddressHtml)))
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 10px;font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";", "📦 Co teraz?")
                                + steps(
                                "Przygotuj wszystkie produkty z zamówienia.",
                                "Zapakuj je bezpiecznie, aby dotarły w dobrym stanie.",
                                "Wyślij przesyłkę na podany adres odbiorcy.",
                                "Po nadaniu przesyłki zaktualizuj status wysyłki w Kasoa.pl.",
                                "Zachowaj potwierdzenie nadania przesyłki."))
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 6px;font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";", "💰 Płatność")
                                + el("div", "font-size:14px;line-height:22px;color:" + MUTED + ";",
                                "Środki zostaną rozliczone zgodnie z zasadami Kasoa po zakończeniu procesu dostawy "
                                        + "i potwierdzeniu odbioru przez kupującego."))
                        + button(HOME_URL, "Przejdź do Kasoa.pl");

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

                productsHtml.append(productRow(productName, "Ilość: " + quantity,
                        productTotal.toPlainString() + " PLN"));
            }

        } else {
            productsHtml.append("<tr><td colspan=\"2\" style=\"padding:16px 0;font-size:14px;color:" + MUTED
                    + ";\">Brak dostępnych produktów.</td></tr>");
        }

        // Personal collection has no courier shipping charge.
        BigDecimal orderTotal = productsTotal;

        String shipmentUrl = shipmentConfirmationUrl
                + "/create?token="
                + URLEncoder.encode(
                shipmentToken,
                StandardCharsets.UTF_8
        );

        String subject =
                "Kasoa.pl — Nowe zamówienie #" + safeOrderNumber;

        String html =
                badge("NOWE ZAMÓWIENIE", Tone.NEUTRAL)
                        + h1("Cześć " + safeSellerName + "! 👋")
                        + lead("Otrzymałeś nowe zamówienie. Kupujący wybrał " + strong("odbiór osobisty")
                        + " zamiast dostawy kurierskiej.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(safeOrderNumber))
                        + h2("Produkty")
                        + table(productsHtml.toString()
                        + kvRow("Produkty", escapeHtml(productsTotal.toPlainString()) + " PLN")
                        + kvTotal("Razem", escapeHtml(orderTotal.toPlainString()) + " PLN"))
                        + section("📍 Potwierdź odbiór osobisty",
                        p("Masz " + strong("24 godziny") + " na potwierdzenie zamówienia i podanie szczegółów odbioru.")
                                + steps(
                                "Otwórz szczegóły zamówienia za pomocą przycisku poniżej.",
                                "Potwierdź zamówienie, podaj dokładny adres odbioru oraz określ dostępne dni i godziny, "
                                        + "w których kupujący może odebrać produkty.",
                                "Po Twoim potwierdzeniu Kasoa wyśle kupującemu wiadomość e-mail ze szczegółami odbioru.",
                                "Kupujący będzie miał " + strong("48 godzin od momentu Twojego potwierdzenia")
                                        + ", aby odebrać zamówienie.",
                                "Po fizycznym odebraniu produktów kupujący potwierdzi ich otrzymanie, klikając bezpieczny link "
                                        + "w wiadomości e-mail od Kasoa.pl."))
                        + button(shipmentUrl, "Potwierdź zamówienie i podaj szczegóły odbioru")
                        + caption("Adres odbioru i dostępne terminy podasz po otwarciu szczegółów zamówienia.")
                        + section("💰 Płatność",
                        muted("Środki zostaną rozliczone zgodnie z zasadami Kasoa po potwierdzeniu odbioru produktów przez kupującego."))
                        + section("Dokończ konfigurację płatności",
                        muted("Aby otrzymywać wypłaty za sprzedane produkty, skonfiguruj konto Stripe i zweryfikuj swoją "
                                + "tożsamość, jeśli jeszcze tego nie zrobiłeś.")
                                + spacer(10)
                                + muted("Zaloguj się na Kasoa.pl, przejdź do " + strong("Konto &gt; Profil")
                                + " i rozpocznij konfigurację Stripe. Następnie wykonaj wymagane kroki weryfikacji tożsamości."));

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(html)
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

                productsHtml.append(productRow(productName, "Ilość: " + quantity,
                        productTotal.toPlainString() + " PLN"));
            }
        } else {
            productsHtml.append("<tr><td colspan=\"2\" style=\"padding:16px 0;font-size:14px;color:" + MUTED
                    + ";\">Brak dostępnych produktów.</td></tr>");
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

        String html =
                badge("NOWE ZAMÓWIENIE", Tone.NEUTRAL)
                        + h1("Cześć " + safeSellerName + "! 👋")
                        + lead("Otrzymałeś nowe zamówienie. Przygotuj produkty do wysyłki, a następnie utwórz przesyłkę "
                        + "InPost za pomocą przycisku poniżej.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(safeOrderNumber))
                        + h2("Produkty")
                        + table(productsHtml.toString()
                        + kvRow("Produkty", escapeHtml(productsTotal.toPlainString()) + " PLN")
                        + kvRow("Dostawa", escapeHtml(shippingTotal.toPlainString()) + " PLN")
                        + kvTotal("Razem", escapeHtml(orderTotal.toPlainString()) + " PLN"))
                        + section("📦 Przygotuj wysyłkę",
                        muted("Przygotuj bezpiecznie zapakowany produkt. Następnie utwórz przesyłkę za pomocą przycisku poniżej."))
                        + button(shipmentUrl, "Utwórz zlecenie wysyłki")
                        + caption("Dane odbiorcy, nadawcy oraz szczegóły przesyłki zostaną automatycznie uzupełnione przez Kasoa.")
                        + section("💰 Płatność",
                        muted("Środki zostaną rozliczone zgodnie z zasadami Kasoa po zakończeniu procesu dostawy "
                                + "i potwierdzeniu odbioru przez kupującego."))
                        + section("Dokończ konfigurację płatności",
                        muted("Aby otrzymywać wypłaty za sprzedane produkty, skonfiguruj konto Stripe i zweryfikuj swoją "
                                + "tożsamość, jeśli jeszcze tego nie zrobiłeś.")
                                + spacer(10)
                                + muted("Zaloguj się na Kasoa.pl, przejdź do " + strong("Konto &gt; Profil")
                                + " i rozpocznij konfigurację Stripe. Następnie wykonaj wymagane kroki weryfikacji tożsamości."));

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(html)
        );
    }


    // =====================================================================
    //  RETURNS & COMPLAINTS
    // =====================================================================

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

        String complaintUrl = frontEndUrl + "/account/orders";

        String subject =
                "Kasoa.pl — Zgłoszenie reklamacji zwrotu #" + safeOrderNumber;

        String htmlContent =
                badge("⚠ Wymagana odpowiedź", Tone.ALERT)
                        + h1("Otrzymano reklamację zwrotu")
                        + lead("Kupujący zgłosił reklamację dotyczącą przedmiotu z zamówienia " + strong("#" + safeOrderNumber)
                        + ". Zaloguj się na swoje konto Kasoa.pl, zapoznaj się ze zgłoszeniem i zdjęciami kupującego, "
                        + "a następnie zaakceptuj reklamację lub ją odrzuć, podając uzasadnienie.")
                        + panel(Tone.NEUTRAL, label("Numer zamówienia") + orderNumber(safeOrderNumber))
                        + quotePanel("Powód reklamacji", safeReason)
                        + section("Co należy zrobić?", steps(
                        "Zaloguj się na swoje konto Kasoa.pl i otwórz szczegóły zamówienia.",
                        "Dokładnie sprawdź opis reklamacji oraz zdjęcia załączone przez kupującego.",
                        "Zaakceptuj reklamację, jeśli uznajesz ją za zasadną. Jeśli się nie zgadzasz, odrzuć ją i podaj "
                                + "uzasadnienie. Sprawa zostanie wówczas przekazana do oceny administracji Kasoa.pl."))
                        + spacer(24)
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 8px;font-size:15px;line-height:22px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "⚖️ Co się stanie po odrzuceniu reklamacji?")
                                + el("div", "margin:0 0 10px;font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Jeśli odrzucisz reklamację, administracja Kasoa.pl przeanalizuje zgłoszenie oraz Twoje "
                                        + "uzasadnienie i podejmie decyzję.")
                                + el("div", "margin:0 0 10px;font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Jeśli administracja uzna, że reklamacja jest zasadna, otrzymasz informację o konieczności "
                                        + "opłacenia przesyłki kurierskiej w celu odebrania przedmiotu od kupującego i zwrócenia go Tobie.")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Kupujący otrzyma pełny zwrot ceny zakupu przedmiotu. Koszt przesyłki zwrotnej od kupującego "
                                        + "do Ciebie poniesiesz Ty jako sprzedający."))
                        + button(complaintUrl, "Otwórz swoje zamówienia →");

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(htmlContent)
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

        String html =
                h1("Aktualizacja dotycząca zwrotu lub reklamacji")
                        + lead("Sprzedający rozpatrzył Twój wniosek dotyczący zwrotu lub reklamacji i go odrzucił. "
                        + "Poniżej znajdziesz odpowiedź sprzedającego.")
                        + panel(Tone.NEUTRAL,
                        label("Numer zamówienia")
                                + orderNumber(safeOrderNumber)
                                + divider()
                                + label("Decyzja sprzedającego")
                                + el("div", "font-size:15px;line-height:23px;font-weight:700;color:" + Tone.ALERT.fg + ";", safeDecision)
                                + divider()
                                + label("Uzasadnienie sprzedającego")
                                + el("div", "font-size:14px;line-height:23px;color:" + BODY + ";white-space:pre-line;overflow-wrap:anywhere;", safeReason))
                        + section("Sprawa jest w trakcie weryfikacji",
                        p("Twoja sprawa została przekazana administratorowi Kasoa.pl do dalszego rozpatrzenia.")
                                + p("Administrator przeanalizuje uzasadnienie sprzedającego, przesłane przez Ciebie zdjęcia "
                                + "dowodowe oraz pierwotne ogłoszenie produktu przed podjęciem ostatecznej decyzji.")
                                + muted("Powiadomimy Cię po zakończeniu weryfikacji. Odrzucenie wniosku przez sprzedającego "
                                + "nie oznacza, że Kasoa.pl podjęło już ostateczną decyzję w Twojej sprawie."))
                        + spacer(24)
                        + p("Dziękujemy za cierpliwość podczas rozpatrywania sprawy.")
                        + el("p", "margin:0;font-size:14px;line-height:22px;color:" + BODY + ";",
                        "Pozdrawiamy,<br>" + strong("Zespół Kasoa.pl"));

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
                ? "Administrator Kasoa.pl rozpatrzył Twój wniosek dotyczący zwrotu lub reklamacji i go zatwierdził."
                : "Administrator Kasoa.pl rozpatrzył Twój wniosek dotyczący zwrotu lub reklamacji i go odrzucił.";

        String nextSteps = approved
                ? section("Co dalej?",
                p("Przygotuj produkt do zwrotu i zapakuj go w sposób zapewniający odpowiednią ochronę podczas transportu.")
                        + p("Kurier odbierze przesyłkę spod tego samego adresu, na który produkt został pierwotnie dostarczony. "
                        + "Koszt odbioru przesyłki przez kuriera pokrywa sprzedający.")
                        + el("p", "margin:0;font-size:15px;line-height:24px;color:" + BODY + ";",
                        "Poczekaj na dalsze instrukcje od Kasoa.pl dotyczące odbioru. Nie wysyłaj produktu samodzielnie, "
                                + "chyba że otrzymasz takie polecenie."))
                : el("p", "margin:20px 0 0;font-size:15px;line-height:24px;color:" + BODY + ";",
                "Jeśli masz pytania dotyczące tej decyzji, skontaktuj się z obsługą Kasoa.pl, podając numer zamówienia.");

        String html =
                badge(approved ? "✓ ZATWIERDZONO" : "ODRZUCONO", approved ? Tone.NEUTRAL : Tone.ALERT)
                        + h1("Decyzja dotycząca zwrotu lub reklamacji")
                        + lead(escapeHtml(decisionMessage))
                        + panel(Tone.NEUTRAL,
                        label("Numer zamówienia")
                                + orderNumber(safeOrderNumber)
                                + divider()
                                + label("Decyzja administratora")
                                + el("div", "font-size:15px;line-height:23px;font-weight:700;color:"
                                + (approved ? BRAND : Tone.ALERT.fg) + ";", safeDecision)
                                + divider()
                                + label("Uzasadnienie decyzji")
                                + el("div", "font-size:14px;line-height:23px;color:" + BODY + ";white-space:pre-line;overflow-wrap:anywhere;", safeReason))
                        + nextSteps
                        + spacer(24)
                        + el("p", "margin:0;font-size:14px;line-height:22px;color:" + BODY + ";",
                        "Pozdrawiamy,<br>" + strong("Zespół Kasoa.pl"))
                        + divider()
                        + muted("Kasoa.pl — prostszy sposób na kupowanie i sprzedawanie.<br>"
                        + "To automatyczne powiadomienie dotyczące Twojego zamówienia.");

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

        String paymentUrl = stripePaymentUrl != null ? stripePaymentUrl : "";

        String subject = "Kasoa.pl — Opłać przesyłkę zwrotną dla zamówienia #"
                + safeOrderNumber;

        String htmlContent =
                badge("Wymagana płatność", Tone.ALERT)
                        + h1("Opłać przesyłkę zwrotną")
                        + lead("Zwrot przedmiotu z zamówienia " + strong("#" + safeOrderNumber)
                        + " został zaakceptowany. Aby zorganizować odbiór przedmiotu od kupującego i jego dostarczenie do Ciebie, "
                        + "opłać przesyłkę zwrotną za pomocą bezpiecznej strony płatności Stripe.")
                        + panel(Tone.NEUTRAL,
                        label("Numer zamówienia")
                                + orderNumber(safeOrderNumber)
                                + divider()
                                + label("Numer zgłoszenia")
                                + el("div", "font-family:" + MONO + ";font-size:14px;line-height:22px;font-weight:700;color:" + INK
                                + ";overflow-wrap:anywhere;", safeComplaintId))
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 8px;font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";", "Co należy zrobić?")
                                + steps(
                                "Kliknij przycisk poniżej i opłać przesyłkę zwrotną.",
                                "Po potwierdzeniu płatności Kasoa.pl zorganizuje odbiór przedmiotu od kupującego i jego zwrot do Ciebie."))
                        + muted("Zwrot środków kupującemu zostanie obsłużony zgodnie z zasadami obowiązującymi dla tego zwrotu. "
                        + "Koszt przesyłki zwrotnej ponosisz jako sprzedający, zgodnie z ustaleniami dotyczącymi tej sprawy.")
                        + button(paymentUrl, "Opłać przesyłkę zwrotną →")
                        + caption("Jeśli przycisk nie działa, skopiuj poniższy link i otwórz go w przeglądarce:")
                        + el("p", "margin:8px 0 0;font-size:11px;line-height:17px;text-align:center;overflow-wrap:anywhere;word-break:break-all;",
                        "<a href=\"" + escapeHtml(paymentUrl) + "\" style=\"color:" + BRAND + ";text-decoration:underline;\">"
                                + escapeHtml(paymentUrl) + "</a>");

        sendHtmlEmail(
                sellerEmail,
                subject,
                buildEmailLayout(htmlContent)
        );
    }


    // =====================================================================
    //  SHIPMENT STATUS (shared body for the three variants below)
    // =====================================================================

    /**
     * Receipt-confirmation card shown once an item is shipped / delivered.
     * extraHtml is optional, already-escaped content rendered between the intro and the button.
     */
    private String receiptCard(String title, String intro, String extraHtml, String url, String footnote) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"width:100%;margin:0 0 24px;border-collapse:separate;\"><tr>"
                + "<td align=\"center\" style=\"padding:28px 24px;background-color:#F7F7F4;border:1px solid " + LINE
                + ";border-radius:14px;text-align:center;font-family:" + FONT + ";\">"
                + el("div", "margin:0 0 8px;font-size:30px;line-height:36px;", "📦")
                + el("div", "margin:0 0 8px;font-size:19px;line-height:27px;font-weight:700;color:" + INK + ";", title)
                + el("div", "margin:0 0 18px;font-size:14px;line-height:22px;color:" + MUTED + ";", intro)
                + extraHtml
                + buttonHtml(url, "✓ Potwierdź odbiór przedmiotu", Variant.PRIMARY, false, "0 auto")
                + el("div", "margin-top:14px;font-size:12px;line-height:18px;color:" + FAINT + ";", footnote)
                + "</td></tr></table>";
    }

    private String buildShipmentStatusHtml(
            String safeBuyerName,
            String safeShipmentStatus,
            String safeOrderNumber,
            String safeProductName,
            String safeTrackingNumber,
            String safeShippingMethod,
            String receiptConfirmationHtml,
            String safeNextStepText
    ) {
        boolean hasTracking = safeTrackingNumber != null && !safeTrackingNumber.isBlank();
        boolean hasMethod = safeShippingMethod != null && !safeShippingMethod.isBlank();

        String shipmentInfo = (hasTracking || hasMethod)
                ? section("📦 Informacje o przesyłce",
                (hasTracking ? infoBlock("Numer przesyłki", safeTrackingNumber) : "")
                        + (hasMethod ? infoBlock("Sposób dostawy", safeShippingMethod) : ""))
                : "";

        return h1("Cześć " + safeBuyerName + "! 👋")
                + lead("Mamy aktualizację dotyczącą Twojego zamówienia.")
                + badge(safeShipmentStatus, Tone.NEUTRAL)
                + panel(Tone.NEUTRAL,
                label("Zamówienie")
                        + el("div", "margin:0 0 16px;font-family:" + MONO + ";font-size:18px;line-height:26px;font-weight:700;color:" + INK
                        + ";overflow-wrap:anywhere;", "#" + safeOrderNumber)
                        + label("Przedmiot")
                        + el("div", "font-size:16px;line-height:23px;font-weight:600;color:" + INK + ";overflow-wrap:anywhere;", safeProductName))
                + receiptConfirmationHtml
                + shipmentInfo
                + section("🔒 Bezpieczna transakcja",
                muted("Płatność jest zabezpieczona w systemie kasoa.pl. Środki zostaną rozliczone zgodnie z zasadami "
                        + "transakcji po zakończeniu procesu."))
                + section("Co dalej?", muted(safeNextStepText))
                + button(HOME_URL, "Przejdź do kasoa.pl →");
    }

    private static final String RECEIPT_HINT =
            "Potwierdź odbiór dopiero po otrzymaniu i sprawdzeniu przesyłki.";

    private String legacyReceiptCard(String shipmentStatus, String confirmationBaseUrl, String receiptConfirmationToken) {
        boolean confirmationStatus =
                "SHIPPED".equalsIgnoreCase(shipmentStatus)
                        || "DELIVERED".equalsIgnoreCase(shipmentStatus);

        boolean hasConfirmationToken =
                receiptConfirmationToken != null
                        && !receiptConfirmationToken.isBlank();

        if (!confirmationStatus || !hasConfirmationToken) {
            return "";
        }

        /*
         * The URL does NOT directly confirm the order.
         * It opens a confirmation page where the buyer must explicitly
         * confirm receipt.
         */
        String receiptConfirmationUrl =
                confirmationBaseUrl
                        + "/confirm-receipt?token="
                        + URLEncoder.encode(
                        receiptConfirmationToken,
                        StandardCharsets.UTF_8
                );

        String confirmationTitle = "DELIVERED".equalsIgnoreCase(shipmentStatus)
                ? "Przesyłka została dostarczona"
                : "Otrzymałeś przesyłkę?";

        return receiptCard(
                escapeHtml(confirmationTitle),
                "Otrzymałeś przesyłkę? Sprawdź przedmiot i jeśli wszystko jest zgodne z zamówieniem, potwierdź odbiór.",
                "",
                receiptConfirmationUrl,
                RECEIPT_HINT
        );
    }

    private String shipmentSubject(String shipmentStatus, String orderNumber) {
        switch (shipmentStatus.toUpperCase()) {
            case "SHIPPED":
                return "Twoje zamówienie #" + orderNumber + " zostało wysłane 📦";
            case "DELIVERED":
                return "Twoje zamówienie #" + orderNumber + " zostało dostarczone ✓";
            case "RECEIVED":
                return "Odbiór zamówienia #" + orderNumber + " został potwierdzony ✓";
            default:
                return "Aktualizacja zamówienia #" + orderNumber;
        }
    }

    /**
     * Legacy variant kept for existing callers. Prefer {@link #sendShipmentStatusToBuyer}.
     */
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
        String subject = shipmentSubject(shipmentStatus, orderNumber);

        String html = buildShipmentStatusHtml(
                escapeHtml(buyerName),
                escapeHtml(formatShipmentStatus(shipmentStatus)),
                escapeHtml(orderNumber),
                escapeHtml(productName),
                escapeHtml(trackingNumber),
                escapeHtml(shippingMethod),
                legacyReceiptCard(shipmentStatus, deliveryConfirmationUrl, receiptConfirmationToken),
                escapeHtml(getNextStepText(shipmentStatus))
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(html));
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

        String html =
                h1("Cześć!")
                        + lead("Zauważyliśmy zainteresowanie Twoim produktem. Zanim kupujący złoży zamówienie, chcemy upewnić się, "
                        + "że oferta jest nadal aktualna.")
                        + panel(Tone.NEUTRAL, label("Twoja oferta")
                        + el("div", "font-size:17px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProduct))
                        + p("Wybierz jedną z poniższych opcji:")
                        + "<ul style=\"margin:0 0 22px;padding-left:20px;font-size:14px;line-height:23px;color:" + BODY + ";\">"
                        + "<li style=\"padding-bottom:8px;\">Potwierdź, jeśli produkt jest nadal dostępny.</li>"
                        + "<li>Zaznacz, że został sprzedany poza Kasoa lub nie jest już dostępny, jeśli nie możesz go wysłać.</li>"
                        + "</ul>"
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 6px;font-size:14px;line-height:20px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "Ważne – uniknij anulowania zamówienia")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Aktualizowanie dostępności pomaga uniknąć zamówień na produkty, których nie możesz już sprzedać. "
                                        + "Anulowanie opłaconego zamówienia może wiązać się z dodatkowymi konsekwencjami."))
                        + h2("Potwierdź dostępność produktu")
                        + buttonFull(availableUrl, "✓ Produkt jest nadal dostępny", Variant.PRIMARY)
                        + buttonFull(soldUrl, "Produkt został sprzedany", Variant.DANGER)
                        + muted("Możesz zmienić swoją decyzję, korzystając z tego samego linku, dopóki pozostaje aktywny.")
                        + section("Co się stanie, jeśli nie odpowiesz?",
                        p("Jeśli nie potwierdzisz dostępności w ciągu 24 godzin, Twoja oferta zostanie przeniesiona do wersji "
                                + "roboczych i przestanie być widoczna dla kupujących.")
                                + el("p", "margin:0;font-size:14px;line-height:23px;color:" + BODY + ";",
                                "W każdej chwili możesz ponownie opublikować produkt w sekcji " + strong("Moje konto → Moje produkty")
                                        + ", zmieniając jego status z " + strong("Wersja robocza") + " na " + strong("Opublikowane") + "."))
                        + spacer(24)
                        + textLink(baseURL, "Przejdź do kasoa.pl →");

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

        String html =
                h1("Kupujący ma pytanie dotyczące Twojego produktu")
                        + lead("Kupujący potrzebuje dodatkowych informacji dotyczących wystawionego przez Ciebie produktu.")
                        + panel(Tone.NEUTRAL, label("Twoja oferta")
                        + el("div", "font-size:17px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProduct))
                        + quotePanel("Pytanie kupującego", safeEnquiry)
                        + p("Uzupełnij brakujące informacje w ogłoszeniu. Po zapisaniu zmian kupujący, którzy poprosili "
                        + "o dodatkowe informacje, otrzymają powiadomienie.")
                        + buttonFull(updateUrl, "Uzupełnij informacje o produkcie →", Variant.PRIMARY)
                        + muted("Link prowadzi bezpośrednio do formularza aktualizacji ogłoszenia.")
                        + section("Produkt został już sprzedany?",
                        muted("Jeśli produkt nie jest już dostępny, oznacz go jako sprzedany, aby kupujący nie składali "
                                + "zamówienia na niedostępny przedmiot.")
                                + spacer(14)
                                + buttonFull(soldUrl, "Oznacz produkt jako sprzedany", Variant.DANGER))
                        + panel(Tone.NEUTRAL, el("div", "font-size:12px;line-height:19px;color:" + MUTED + ";",
                        "Linki w tej wiadomości są przeznaczone wyłącznie dla Ciebie. Nie udostępniaj ich innym osobom."))
                        + muted("Dziękujemy za korzystanie z Kasoa!");

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

        String html =
                h1("Sprzedający zaktualizował informacje o produkcie")
                        + lead("Produkt, o który pytałeś, został zaktualizowany. Sprzedający uzupełnił ogłoszenie o dodatkowe "
                        + "informacje, których potrzebowałeś.")
                        + panel(Tone.NEUTRAL, label("Produkt")
                        + el("div", "font-size:17px;line-height:24px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProduct))
                        + p("Możesz teraz przejść do ogłoszenia i zapoznać się z uzupełnionymi informacjami.")
                        + button(fullProductUrl, "Zobacz produkt")
                        + spacer(24)
                        + muted("Dziękujemy za korzystanie z Kasoa!");

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

            String deadlineBox = panel(Tone.ALERT,
                    el("div", "margin:0 0 8px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";text-align:left;",
                            "⏰ Masz 24 godziny od dostawy")
                            + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";text-align:left;overflow-wrap:anywhere;",
                            strong("Jeśli wszystko jest w porządku:") + "<br>"
                                    + "Kliknij przycisk poniżej, aby potwierdzić odbiór przedmiotu. Nie musisz logować się na swoje konto."
                                    + "<br><br>"
                                    + strong("Jeśli zauważysz istotne rozbieżności:") + "<br>"
                                    + "Nie potwierdzaj odbioru. Zaloguj się na swoje konto Kasoa.pl, przejdź do "
                                    + strong("Konto → Zamówienia") + ", wybierz konkretne zamówienie i złóż wniosek o zwrot, "
                                    + "dołączając zdjęcia lub inne dowody."
                                    + "<br><br>"
                                    + "Drobne różnice wynikające na przykład z oświetlenia lub sposobu wykonania zdjęć nie stanowią "
                                    + "same w sobie podstawy do zwrotu. Zgłoszenie powinno dotyczyć istotnych rozbieżności między "
                                    + "ofertą a otrzymanym przedmiotem."
                                    + "<br><br>"
                                    + strong("Jak rozpatrywany jest wniosek?") + "<br>"
                                    + "Wniosek nie oznacza automatycznej akceptacji zwrotu. Sprzedawca przeanalizuje zgłoszenie "
                                    + "i przedstawione dowody. Jeśli odrzuci wniosek, sprawa zostanie przekazana administratorowi "
                                    + "Kasoa.pl do ostatecznej oceny oferty i zgromadzonych dowodów."));

            receiptConfirmationHtml = receiptCard(
                    "Otrzymałeś przesyłkę?",
                    "Sprawdź otrzymany przedmiot i porównaj go z zamówieniem oraz opisem sprzedawcy.",
                    deadlineBox,
                    receiptConfirmationUrl,
                    "Kliknij przycisk tylko wtedy, gdy otrzymany przedmiot jest zgodny z zamówieniem i opisem."
            ) + el("p", "margin:-8px 0 24px;font-size:12px;line-height:20px;color:" + MUTED + ";",
                    strong("Ważne:") + " Jeśli w ciągu 24 godzin od dostawy nie potwierdzisz odbioru ani nie złożysz wniosku "
                            + "o zwrot, odbiór zostanie automatycznie potwierdzony, a wypłata środków sprzedawcy zostanie uruchomiona.");
        }

        String html = buildShipmentStatusHtml(
                safeBuyerName,
                escapeHtml(statusLabel),
                escapeHtml(orderNumber),
                escapeHtml(productName),
                escapeHtml(trackingNumber),
                escapeHtml(shippingMethod),
                receiptConfirmationHtml,
                escapeHtml(getNextStepText(status))
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

        String html =
                badge("ODBIÓR OSOBISTY", Tone.ALERT)
                        + h1("Odbiór osobisty nie został zrealizowany")
                        + p("Dzień dobry " + safeBuyerName + ",")
                        + p("Sprzedający poinformował nas, że odbiór osobisty przedmiotu nie doszedł do skutku.")
                        + panel(Tone.NEUTRAL,
                        label("Numer zamówienia")
                                + el("div", "margin:0 0 14px;font-family:" + MONO + ";font-size:16px;line-height:24px;font-weight:700;color:" + INK + ";", "#" + safeOrderNumber)
                                + label("Przedmiot")
                                + el("div", "font-size:15px;line-height:22px;font-weight:700;color:" + INK + ";overflow-wrap:anywhere;", safeProductName))
                        + section("Co należy teraz zrobić?",
                        p("Zaloguj się na swoje konto Kasoa i przejdź do sekcji " + strong("Moje konto → Zamówienia")
                                + ". Następnie zainicjuj zgłoszenie zwrotu dla tego zamówienia.")
                                + p("W zgłoszeniu:")
                                + "<ul style=\"margin:0 0 16px;padding-left:20px;font-size:14px;line-height:24px;color:" + BODY + ";\">"
                                + "<li>wybierz odpowiedni powód zgłoszenia,</li>"
                                + "<li>opisz, co wydarzyło się podczas próby odbioru,</li>"
                                + "<li>dołącz zdjęcia lub inne dostępne dowody, jeśli je posiadasz.</li>"
                                + "</ul>"
                                + p("Sprzedający będzie mógł odnieść się do zgłoszenia. Jeżeli nie zgodzi się z jego treścią, "
                                + "sprawa zostanie przekazana do weryfikacji przez Kasoa.")
                                + el("p", "margin:0;font-size:15px;line-height:24px;color:" + BODY + ";",
                                "Samo zgłoszenie nie oznacza automatycznego zatwierdzenia zwrotu. Sprawa zostanie rozpatrzona "
                                        + "na podstawie przedstawionych informacji i dostępnych dowodów."))
                        + button(ordersUrl, "Przejdź do moich zamówień")
                        + spacer(28)
                        + muted("Jeśli masz pytania, skontaktuj się z naszym zespołem obsługi klienta.")
                        + spacer(16)
                        + el("p", "margin:0;font-size:14px;line-height:22px;color:" + BODY + ";",
                        "Pozdrawiamy,<br>" + strong("Zespół Kasoa"));

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(html));
    }


    /**
     * Legacy variant kept for existing callers. Note: despite the name it addresses
     * the buyer and uses the return confirmation URL, exactly as before.
     */
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
        String subject = shipmentSubject(shipmentStatus, orderNumber);

        String html = buildShipmentStatusHtml(
                escapeHtml(buyerName),
                escapeHtml(formatShipmentStatus(shipmentStatus)),
                escapeHtml(orderNumber),
                escapeHtml(productName),
                escapeHtml(trackingNumber),
                escapeHtml(shippingMethod),
                legacyReceiptCard(shipmentStatus, returnConfirmationUrl, receiptConfirmationToken),
                escapeHtml(getNextStepText(shipmentStatus))
        );

        sendHtmlEmail(buyerEmail, subject, buildEmailLayout(html));
    }


    public void notifySellerToStartVerification(String email, String name, String accountUrl) {

        String safeName = escapeHtml(
                name != null && !name.isBlank() ? name : "Sprzedawco"
        );

        String subject =
                "Dokończ weryfikację, aby otrzymywać wypłaty – kasoa.pl";

        String html =
                h1("Cześć " + safeName + "! 👋")
                        + lead("Aby móc otrzymywać wypłaty ze sprzedaży na kasoa.pl, musisz rozpocząć proces weryfikacji "
                        + "tożsamości sprzedawcy.")
                        + panel(Tone.NEUTRAL,
                        el("div", "margin:0 0 8px;font-size:17px;line-height:25px;font-weight:700;color:" + INK + ";",
                                "🔐 Weryfikacja tożsamości sprzedawcy")
                                + el("div", "font-size:14px;line-height:23px;color:" + MUTED + ";",
                                "Weryfikacja jest wymagana, aby umożliwić realizację wypłat. Rozpocznij onboarding i wykonaj "
                                        + "wymagane kroki zgodnie z wyświetlanymi instrukcjami."))
                        + section("Jak rozpocząć weryfikację?", steps(
                        "Zaloguj się na swoje konto kasoa.pl.",
                        "Przejdź do sekcji " + strong("Konto → Profil") + ".",
                        "Otwórz sekcję " + strong("Weryfikacja tożsamości sprzedawcy") + ".",
                        "Kliknij przycisk rozpoczynający onboarding i postępuj zgodnie z instrukcjami."))
                        + spacer(24)
                        + panel(Tone.ALERT,
                        el("div", "margin:0 0 6px;font-size:14px;line-height:21px;font-weight:700;color:" + Tone.ALERT.fg + ";",
                                "Ważne informacje o wypłatach")
                                + el("div", "font-size:13px;line-height:21px;color:" + BODY + ";",
                                "Do czasu ukończenia wymaganej weryfikacji i uzyskania odpowiedniego statusu zatwierdzenia "
                                        + "możliwość otrzymywania wypłat może być niedostępna."))
                        + button(accountUrl, "Przejdź do swojego konta →")
                        + caption("Jeśli rozpocząłeś już weryfikację, dokończ proces, aby umożliwić realizację wypłat.");

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

        html.append("<tr><td colspan=\"2\" style=\"padding:0 0 8px;font-family:").append(FONT)
                .append(";font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.8px;text-transform:uppercase;color:")
                .append(BRAND).append(";\">").append(escapeHtml(label)).append("</td></tr>");

        html.append(addressLine(escapeHtml(name), true));

        for (String line : lines) {

            if (line == null || line.isBlank()) {
                continue;
            }

            html.append(addressLine(escapeHtml(line.trim()), false));
        }

        if (phone != null && !phone.isBlank()) {
            html.append(addressLine(escapeHtml(phone), false));
        }

        return html.toString();
    }

    private String addressLine(String safeText, boolean bold) {
        return "<tr><td colspan=\"2\" style=\"padding:1px 0;font-family:" + FONT
                + ";font-size:14px;line-height:22px;" + (bold ? "font-weight:700;" : "") + "color:" + INK + ";\">"
                + safeText + "</td></tr>";
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

            String html =
                    badge("✓ Wypłata zrealizowana", Tone.NEUTRAL)
                            + h1("Przekazaliśmy środki ze sprzedaży")
                            + lead("Środki należne Ci za zamówienie zostały przekazane na Twoje konto Stripe. "
                            + "Poniżej znajdziesz szczegółowe rozliczenie:")
                            + panel(Tone.NEUTRAL, table(
                            kvRow("Numer zamówienia", safeOrderNumber)
                                    + kvRow("Kwota brutto sprzedającego", safeSellerPrice)
                                    + kvRow("Opłata Stripe za bieżące zamówienie", "− " + safeStripeFee)
                                    + kvRow("Nieodzyskane opłaty Stripe z anulowanych zamówień", "− " + safeAmountToRecover)
                                    + kvTotal("Kwota netto przekazana", safeNetAmount)))
                            + label("Identyfikator transferu Stripe")
                            + el("div", "margin:0 0 24px;font-family:" + MONO + ";font-size:12px;line-height:19px;color:" + MUTED
                            + ";overflow-wrap:anywhere;word-break:break-all;", safeTransferId)
                            + muted("Możesz sprawdzić szczegóły przekazania środków na swoim koncie Stripe.");

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
