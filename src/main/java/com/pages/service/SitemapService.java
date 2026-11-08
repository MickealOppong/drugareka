package com.pages.service;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.Category;
import com.pages.model.ListingTransaction;
import com.pages.repository.CategoryRepo;
import com.pages.repository.ListingTransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SitemapService {

    private static final String SITEMAP_NAMESPACE =
            "http://www.sitemaps.org/schemas/sitemap/0.9";

    private static final int PAGE_SIZE = 500;

    // Sitemap protocol limit: 50,000 URLs per sitemap.
    private static final int MAX_URLS = 50_000;

    private final ListingTransactionRepo listingTransactionRepo;
    private final CategoryRepo categoryRepo;

    @Value("${app.sitemap.base-url}")
    private String baseUrl;

    public String generateSitemap() {

        StringWriter output = new StringWriter();

        try {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(output);

            writer.writeStartDocument("UTF-8", "1.0");

            writer.writeStartElement("urlset");
            writer.writeDefaultNamespace(SITEMAP_NAMESPACE);

            int urlCount = 0;

            // Static public pages
            urlCount += writeStaticPages(writer);

            // Active categories
            List<Category> categories =
                    categoryRepo.findActiveCategoriesForSitemap();

            for (Category category : categories) {

                if (urlCount >= MAX_URLS) {
                    break;
                }

                String url = getCategoryUrl(category);

                if (url == null) {
                    continue;
                }

                writeUrl(
                        writer,
                        url,
                        getLastModified(
                                category.getModifiedAt(),
                                category.getCreatedAt()
                        )
                );

                urlCount++;
            }

            // Published listings with available inventory
            if (urlCount < MAX_URLS) {
                urlCount += writeListings(writer, MAX_URLS - urlCount);
            }

            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            writer.close();

            return output.toString();

        } catch (XMLStreamException e) {
            throw new IllegalStateException(
                    "Unable to generate sitemap XML", e
            );
        }
    }

    private int writeStaticPages(XMLStreamWriter writer)
            throws XMLStreamException {

        writeUrl(writer, baseUrl + "/", null);
        writeUrl(writer, baseUrl + "/shop", null);
        writeUrl(writer, baseUrl + "/shop/categories", null);

        return 3;
    }

    private int writeListings(XMLStreamWriter writer, int remaining)
            throws XMLStreamException {

        int written = 0;
        int pageNumber = 0;

        while (written < remaining) {

            Page<ListingTransaction> page =
                    listingTransactionRepo.findSitemapListings(
                            ListingStatus.PUBLISHED,
                            InventoryStatus.AVAILABLE,
                            PageRequest.of(
                                    pageNumber,
                                    PAGE_SIZE,
                                    Sort.by(
                                            Sort.Direction.DESC,
                                            "createdAt"
                                    )
                            )
                    );

            if (page.isEmpty()) {
                break;
            }

            for (ListingTransaction listing : page.getContent()) {

                if (written >= remaining) {
                    break;
                }

                if (listing.getInventory() == null
                        || listing.getInventory().getProductCatalog() == null) {
                    continue;
                }

                String slug = listing.getInventory()
                        .getProductCatalog()
                        .getSlug();

                if (slug == null || slug.isBlank()) {
                    continue;
                }

                String url = baseUrl + "/shop/listing/" + slug;

                Instant lastModified = getLastModified(
                        listing.getModifiedAt(),
                        listing.getCreatedAt()
                );

                writeUrl(writer, url, lastModified);

                written++;
            }

            if (!page.hasNext()) {
                break;
            }

            pageNumber++;
        }

        return written;
    }

    private String getCategoryUrl(Category category) {

        if (category.getSlug() == null || category.getSlug().isBlank()
                || category.getId() == null) {
            return null;
        }

        String categoryKey = category.getSlug() + "_" + category.getId();

        return baseUrl
                + "/shop/categories?category="
                + URLEncoder.encode(categoryKey, StandardCharsets.UTF_8);
    }

    private Instant getLastModified(
            Instant modifiedAt,
            Instant createdAt) {

        return modifiedAt != null ? modifiedAt : createdAt;
    }

    private void writeUrl(
            XMLStreamWriter writer,
            String url,
            Instant lastModified) throws XMLStreamException {

        writer.writeStartElement("url");

        writer.writeStartElement("loc");
        writer.writeCharacters(url);
        writer.writeEndElement();

        if (lastModified != null) {

            writer.writeStartElement("lastmod");
            writer.writeCharacters(
                    DateTimeFormatter.ISO_INSTANT.format(lastModified)
            );
            writer.writeEndElement();
        }

        writer.writeEndElement();
    }
}