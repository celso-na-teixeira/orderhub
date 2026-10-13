package com.orderhub.mapper;

import com.orderhub.domain.Product;
import com.orderhub.dto.CreateProductRequest;
import com.orderhub.dto.ProductResponse;
import com.orderhub.dto.UpdateProductRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface ProductMapper {

    @Mapping(target = "availableQuantity", source = "inventory.availableQuantity")
    ProductResponse toResponse(Product product);

    @Mapping(target = "active", ignore = true)
    Product toEntity(CreateProductRequest request);

    /**
     * Partial update: only the fields present in the request change.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyUpdate(UpdateProductRequest request, @MappingTarget Product product);
}
