/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
/**
 * Autogenerated by Thrift Compiler (0.14.1)
 * <p>
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.1)", date = "2021-05-26")
public class GetFileInfoRequest
    implements org.apache.thrift.TBase<GetFileInfoRequest, GetFileInfoRequest._Fields>, java.io.Serializable, Cloneable,
    Comparable<GetFileInfoRequest> {
    // isset id assignments
    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("GetFileInfoRequest");
    private static final org.apache.thrift.protocol.TField BUCKET_NAME_FIELD_DESC =
        new org.apache.thrift.protocol.TField("bucketName", org.apache.thrift.protocol.TType.STRING, (short) 1);
    private static final org.apache.thrift.protocol.TField FILE_PATH_FIELD_DESC =
        new org.apache.thrift.protocol.TField("filePath", org.apache.thrift.protocol.TType.STRING, (short) 2);
    private static final org.apache.thrift.protocol.TField AUTH_FIELD_DESC =
        new org.apache.thrift.protocol.TField("auth", org.apache.thrift.protocol.TType.STRUCT, (short) 3);
    private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY =
        new GetFileInfoRequestStandardSchemeFactory();
    private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY =
        new GetFileInfoRequestTupleSchemeFactory();

    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
            new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        tmpMap.put(_Fields.BUCKET_NAME,
            new org.apache.thrift.meta_data.FieldMetaData("bucketName",
                org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.FILE_PATH,
            new org.apache.thrift.meta_data.FieldMetaData("filePath", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.AUTH,
            new org.apache.thrift.meta_data.FieldMetaData("auth", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT,
                    Authorization.class)));
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(GetFileInfoRequest.class, metaDataMap);
    }

    public @org.apache.thrift.annotation.Nullable
    String bucketName; // required
    public @org.apache.thrift.annotation.Nullable
    String filePath; // required
    public @org.apache.thrift.annotation.Nullable
    Authorization auth; // required

    public GetFileInfoRequest() {
    }

    public GetFileInfoRequest(
        String bucketName,
        String filePath,
        Authorization auth) {
        this();
        this.bucketName = bucketName;
        this.filePath = filePath;
        this.auth = auth;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public GetFileInfoRequest(GetFileInfoRequest other) {
        if (other.isSetBucketName()) {
            this.bucketName = other.bucketName;
        }
        if (other.isSetFilePath()) {
            this.filePath = other.filePath;
        }
        if (other.isSetAuth()) {
            this.auth = new Authorization(other.auth);
        }
    }

    private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
        return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY :
            TUPLE_SCHEME_FACTORY).getScheme();
    }

    public GetFileInfoRequest deepCopy() {
        return new GetFileInfoRequest(this);
    }

    @Override
    public void clear() {
        this.bucketName = null;
        this.filePath = null;
        this.auth = null;
    }

    @org.apache.thrift.annotation.Nullable
    public String getBucketName() {
        return this.bucketName;
    }

    public GetFileInfoRequest setBucketName(@org.apache.thrift.annotation.Nullable String bucketName) {
        this.bucketName = bucketName;
        return this;
    }

    public void unsetBucketName() {
        this.bucketName = null;
    }

    /**
     * Returns true if field bucketName is set (has been assigned a value) and false otherwise
     */
    public boolean isSetBucketName() {
        return this.bucketName != null;
    }

    public void setBucketNameIsSet(boolean value) {
        if (!value) {
            this.bucketName = null;
        }
    }

    @org.apache.thrift.annotation.Nullable
    public String getFilePath() {
        return this.filePath;
    }

    public GetFileInfoRequest setFilePath(@org.apache.thrift.annotation.Nullable String filePath) {
        this.filePath = filePath;
        return this;
    }

    public void unsetFilePath() {
        this.filePath = null;
    }

    /**
     * Returns true if field filePath is set (has been assigned a value) and false otherwise
     */
    public boolean isSetFilePath() {
        return this.filePath != null;
    }

    public void setFilePathIsSet(boolean value) {
        if (!value) {
            this.filePath = null;
        }
    }

    @org.apache.thrift.annotation.Nullable
    public Authorization getAuth() {
        return this.auth;
    }

    public GetFileInfoRequest setAuth(@org.apache.thrift.annotation.Nullable Authorization auth) {
        this.auth = auth;
        return this;
    }

    public void unsetAuth() {
        this.auth = null;
    }

    /**
     * Returns true if field auth is set (has been assigned a value) and false otherwise
     */
    public boolean isSetAuth() {
        return this.auth != null;
    }

    public void setAuthIsSet(boolean value) {
        if (!value) {
            this.auth = null;
        }
    }

    public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable Object value) {
        switch (field) {
        case BUCKET_NAME:
            if (value == null) {
                unsetBucketName();
            } else {
                setBucketName((String) value);
            }
            break;

        case FILE_PATH:
            if (value == null) {
                unsetFilePath();
            } else {
                setFilePath((String) value);
            }
            break;

        case AUTH:
            if (value == null) {
                unsetAuth();
            } else {
                setAuth((Authorization) value);
            }
            break;

        }
    }

    @org.apache.thrift.annotation.Nullable
    public Object getFieldValue(_Fields field) {
        switch (field) {
        case BUCKET_NAME:
            return getBucketName();

        case FILE_PATH:
            return getFilePath();

        case AUTH:
            return getAuth();

        }
        throw new IllegalStateException();
    }

    /**
     * Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise
     */
    public boolean isSet(_Fields field) {
        if (field == null) {
            throw new IllegalArgumentException();
        }

        switch (field) {
        case BUCKET_NAME:
            return isSetBucketName();
        case FILE_PATH:
            return isSetFilePath();
        case AUTH:
            return isSetAuth();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof GetFileInfoRequest) {
            return this.equals((GetFileInfoRequest) that);
        }
        return false;
    }

    public boolean equals(GetFileInfoRequest that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }

        boolean this_present_bucketName = true && this.isSetBucketName();
        boolean that_present_bucketName = true && that.isSetBucketName();
        if (this_present_bucketName || that_present_bucketName) {
            if (!(this_present_bucketName && that_present_bucketName)) {
                return false;
            }
            if (!this.bucketName.equals(that.bucketName)) {
                return false;
            }
        }

        boolean this_present_filePath = true && this.isSetFilePath();
        boolean that_present_filePath = true && that.isSetFilePath();
        if (this_present_filePath || that_present_filePath) {
            if (!(this_present_filePath && that_present_filePath)) {
                return false;
            }
            if (!this.filePath.equals(that.filePath)) {
                return false;
            }
        }

        boolean this_present_auth = true && this.isSetAuth();
        boolean that_present_auth = true && that.isSetAuth();
        if (this_present_auth || that_present_auth) {
            if (!(this_present_auth && that_present_auth)) {
                return false;
            }
            if (!this.auth.equals(that.auth)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;

        hashCode = hashCode * 8191 + ((isSetBucketName()) ? 131071 : 524287);
        if (isSetBucketName()) {
            hashCode = hashCode * 8191 + bucketName.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetFilePath()) ? 131071 : 524287);
        if (isSetFilePath()) {
            hashCode = hashCode * 8191 + filePath.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetAuth()) ? 131071 : 524287);
        if (isSetAuth()) {
            hashCode = hashCode * 8191 + auth.hashCode();
        }

        return hashCode;
    }

    @Override
    public int compareTo(GetFileInfoRequest other) {
        if (!getClass().equals(other.getClass())) {
            return getClass().getName().compareTo(other.getClass().getName());
        }

        int lastComparison = 0;

        lastComparison = Boolean.compare(isSetBucketName(), other.isSetBucketName());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetBucketName()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.bucketName, other.bucketName);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = Boolean.compare(isSetFilePath(), other.isSetFilePath());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetFilePath()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.filePath, other.filePath);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = Boolean.compare(isSetAuth(), other.isSetAuth());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetAuth()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.auth, other.auth);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        return 0;
    }

    @org.apache.thrift.annotation.Nullable
    public _Fields fieldForId(int fieldId) {
        return _Fields.findByThriftId(fieldId);
    }

    public void read(org.apache.thrift.protocol.TProtocol iprot) throws org.apache.thrift.TException {
        scheme(iprot).read(iprot, this);
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot) throws org.apache.thrift.TException {
        scheme(oprot).write(oprot, this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GetFileInfoRequest(");
        boolean first = true;

        sb.append("bucketName:");
        if (this.bucketName == null) {
            sb.append("null");
        } else {
            sb.append(this.bucketName);
        }
        first = false;
        if (!first) {
            sb.append(", ");
        }
        sb.append("filePath:");
        if (this.filePath == null) {
            sb.append("null");
        } else {
            sb.append(this.filePath);
        }
        first = false;
        if (!first) {
            sb.append(", ");
        }
        sb.append("auth:");
        if (this.auth == null) {
            sb.append("null");
        } else {
            sb.append(this.auth);
        }
        first = false;
        sb.append(")");
        return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
        // check for required fields
        if (bucketName == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'bucketName' was not present! Struct: " + toString());
        }
        if (filePath == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'filePath' was not present! Struct: " + toString());
        }
        if (auth == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'auth' was not present! Struct: " + toString());
        }
        // check for sub-struct validity
        if (auth != null) {
            auth.validate();
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        try {
            write(new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(out)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        try {
            read(new org.apache.thrift.protocol.TCompactProtocol(
                new org.apache.thrift.transport.TIOStreamTransport(in)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }

    /**
     * The set of fields this struct contains, along with convenience methods for finding and manipulating them.
     */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
        BUCKET_NAME((short) 1, "bucketName"),
        FILE_PATH((short) 2, "filePath"),
        AUTH((short) 3, "auth");

        private static final java.util.Map<String, _Fields> byName = new java.util.HashMap<String, _Fields>();

        static {
            for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
                byName.put(field.getFieldName(), field);
            }
        }

        private final short _thriftId;
        private final String _fieldName;

        _Fields(short thriftId, String fieldName) {
            _thriftId = thriftId;
            _fieldName = fieldName;
        }

        /**
         * Find the _Fields constant that matches fieldId, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByThriftId(int fieldId) {
            switch (fieldId) {
            case 1: // BUCKET_NAME
                return BUCKET_NAME;
            case 2: // FILE_PATH
                return FILE_PATH;
            case 3: // AUTH
                return AUTH;
            default:
                return null;
            }
        }

        /**
         * Find the _Fields constant that matches fieldId, throwing an exception
         * if it is not found.
         */
        public static _Fields findByThriftIdOrThrow(int fieldId) {
            _Fields fields = findByThriftId(fieldId);
            if (fields == null) {
                throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
            }
            return fields;
        }

        /**
         * Find the _Fields constant that matches name, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByName(String name) {
            return byName.get(name);
        }

        public short getThriftFieldId() {
            return _thriftId;
        }

        public String getFieldName() {
            return _fieldName;
        }
    }

    private static class GetFileInfoRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public GetFileInfoRequestStandardScheme getScheme() {
            return new GetFileInfoRequestStandardScheme();
        }
    }

    private static class GetFileInfoRequestStandardScheme
        extends org.apache.thrift.scheme.StandardScheme<GetFileInfoRequest> {

        public void read(org.apache.thrift.protocol.TProtocol iprot, GetFileInfoRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TField schemeField;
            iprot.readStructBegin();
            while (true) {
                schemeField = iprot.readFieldBegin();
                if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
                    break;
                }
                switch (schemeField.id) {
                case 1: // BUCKET_NAME
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.bucketName = iprot.readString();
                        struct.setBucketNameIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 2: // FILE_PATH
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.filePath = iprot.readString();
                        struct.setFilePathIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 3: // AUTH
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRUCT) {
                        struct.auth = new Authorization();
                        struct.auth.read(iprot);
                        struct.setAuthIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                default:
                    org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                }
                iprot.readFieldEnd();
            }
            iprot.readStructEnd();

            // check for required fields of primitive type, which can't be checked in the validate method
            struct.validate();
        }

        public void write(org.apache.thrift.protocol.TProtocol oprot, GetFileInfoRequest struct)
            throws org.apache.thrift.TException {
            struct.validate();

            oprot.writeStructBegin(STRUCT_DESC);
            if (struct.bucketName != null) {
                oprot.writeFieldBegin(BUCKET_NAME_FIELD_DESC);
                oprot.writeString(struct.bucketName);
                oprot.writeFieldEnd();
            }
            if (struct.filePath != null) {
                oprot.writeFieldBegin(FILE_PATH_FIELD_DESC);
                oprot.writeString(struct.filePath);
                oprot.writeFieldEnd();
            }
            if (struct.auth != null) {
                oprot.writeFieldBegin(AUTH_FIELD_DESC);
                struct.auth.write(oprot);
                oprot.writeFieldEnd();
            }
            oprot.writeFieldStop();
            oprot.writeStructEnd();
        }

    }

    private static class GetFileInfoRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public GetFileInfoRequestTupleScheme getScheme() {
            return new GetFileInfoRequestTupleScheme();
        }
    }

    private static class GetFileInfoRequestTupleScheme
        extends org.apache.thrift.scheme.TupleScheme<GetFileInfoRequest> {

        @Override
        public void write(org.apache.thrift.protocol.TProtocol prot, GetFileInfoRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            oprot.writeString(struct.bucketName);
            oprot.writeString(struct.filePath);
            struct.auth.write(oprot);
        }

        @Override
        public void read(org.apache.thrift.protocol.TProtocol prot, GetFileInfoRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            struct.bucketName = iprot.readString();
            struct.setBucketNameIsSet(true);
            struct.filePath = iprot.readString();
            struct.setFilePathIsSet(true);
            struct.auth = new Authorization();
            struct.auth.read(iprot);
            struct.setAuthIsSet(true);
        }
    }
}

