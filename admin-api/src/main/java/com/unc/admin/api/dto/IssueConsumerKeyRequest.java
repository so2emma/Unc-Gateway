package com.unc.admin.api.dto;

/**
 * Optional request body for {@code POST /api/admin/consumers/{id}/keys}. An empty body is valid, in
 * which case the issued key is auto-named.
 */
public class IssueConsumerKeyRequest {

    private String name;

    public IssueConsumerKeyRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
