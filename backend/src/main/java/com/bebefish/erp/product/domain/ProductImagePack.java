package com.bebefish.erp.product.domain;

import java.util.List;
import java.util.stream.Stream;

public record ProductImagePack(
        String name,
        List<Long> main,
        List<Long> sku,
        List<Long> detail,
        List<Long> longImages
) {
    public ProductImagePack {
        main = List.copyOf(main);
        sku = List.copyOf(sku);
        detail = List.copyOf(detail);
        longImages = List.copyOf(longImages);
    }

    public Stream<Long> fileIds() {
        return Stream.of(main, sku, detail, longImages).flatMap(List::stream);
    }
}
