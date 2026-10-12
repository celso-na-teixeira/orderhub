# Controller return types

How to choose the return type of a controller method in OrderHub.

## The rule

Return the DTO directly. Wrap it in `ResponseEntity` only when the method has to control the HTTP status or headers itself.

Controllers never return JPA entities. Each entity has a response DTO in `dto/` and a mapper in `mapper/`, and the controller calls the mapper on what the service returns.

## What each return type sends

| Return type | What Spring sends | Use it when |
|---|---|---|
| `OrderResponse` (plain DTO) | 200 with the DTO as JSON | The normal case: always 200, no special headers |
| `ResponseEntity<OrderResponse>` | The status, headers and body you build | You need a non-200 status or a header, e.g. 201 with `Location` |
| `ResponseEntity<Void>` | Status and headers, no body | There is nothing to return, e.g. 204 after a delete |
| `ResponseEntity<?>` | Same, body type unknown | Avoid: Swagger can't document it and the compiler can't check it |

## By HTTP method

| Method | Response | Return type |
|---|---|---|
| `GET` (one or list) | 200 with body | The DTO, `List<DTO>` or `Page<DTO>` directly |
| `POST` that creates | 201 with body and a `Location` header | `ResponseEntity<DTO>` |
| `POST` that creates, no `Location` | 201 with body | The DTO plus `@ResponseStatus(HttpStatus.CREATED)` |
| `POST` that performs an action (refund, validate) | 200 with body | The DTO directly |
| `PUT` / `PATCH` | 200 with the updated resource | The DTO directly |
| `DELETE` | 204 with no body | `ResponseEntity<Void>`, or `void` plus `@ResponseStatus(HttpStatus.NO_CONTENT)` |

The only two cases that call for `ResponseEntity` are a create that sets a `Location` header and a response with no body.

## Examples

Read or update: return the DTO.

```java
@GetMapping("/{id}")
public ProductResponse get(@PathVariable Long id) {
    return productMapper.toResponse(productService.getProduct(id));
}
```

Create with a `Location` header:

```java
@PostMapping
public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
    ProductResponse created = productMapper.toResponse(productService.create(request));
    URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
    return ResponseEntity.created(location).body(created);
}
```

Create without a `Location` header:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public DiscountResponse create(@Valid @RequestBody DiscountRequest request) {
    return discountMapper.toResponse(discountService.create(request));
}
```

Delete:

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.deactivate(id);
    return ResponseEntity.noContent().build();
}
```

## Errors

Errors do not go through the return type. Throw an exception (`ResourceNotFoundException`, `AccessDeniedException`, `BusinessRuleException`, ...) and `GlobalExceptionHandler` turns it into the error response. This is why a method never needs `ResponseEntity<?>` to return "either a DTO or an error".

## Known deviations (as of 2026-10-12)

- `GET /orders/{id}/cancel` changes state, so it should be a `POST`. The README lists it as `POST /orders/{id}/cancel`.
- `InventoryController` has one method that wraps its result in `ResponseEntity.ok(...)`, which is the same as returning the DTO directly.
