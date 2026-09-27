package com.payroll.admin.web.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the whole attributes table as one editable form. A row is dropped
 * entirely when {@code deleted} is ticked; the controller also skips rows that
 * are completely blank so "Add Attribute" rows are safe to submit empty.
 */
public class AttributeForm {

    private List<AttributeRow> attributes = new ArrayList<>();

    public List<AttributeRow> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeRow> attributes) {
        this.attributes = attributes;
    }

    public static class AttributeRow {
        private String id;
        private String displayName;
        private String dataType;
        private String source;
        private String defaultValue;
        private boolean deleted;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }
    }
}