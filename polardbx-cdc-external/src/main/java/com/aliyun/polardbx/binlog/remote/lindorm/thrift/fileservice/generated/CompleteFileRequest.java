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
public class CompleteFileRequest
    implements org.apache.thrift.TBase<CompleteFileRequest, CompleteFileRequest._Fields>, java.io.Serializable,
    Cloneable, Comparable<CompleteFileRequest> {
    // isset id assignments
    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("CompleteFileRequest");
    private static final org.apache.thrift.protocol.TField OUTPUT_STREAM_ID_FIELD_DESC =
        new org.apache.thrift.protocol.TField("outputStreamId", org.apache.thrift.protocol.TType.STRING, (short) 1);
    private static final org.apache.thrift.protocol.TField BUCKET_NAME_FIELD_DESC =
        new org.apache.thrift.protocol.TField("bucketName", org.apache.thrift.protocol.TType.STRING, (short) 2);
    private static final org.apache.thrift.protocol.TField PATH_FIELD_DESC =
        new org.apache.thrift.protocol.TField("path", org.apache.thrift.protocol.TType.STRING, (short) 3);
    private static final org.apache.thrift.protocol.TField AUTH_FIELD_DESC =
        new org.apache.thrift.protocol.TField("auth", org.apache.thrift.protocol.TType.STRUCT, (short) 4);
    private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY =
        new CompleteFileRequestStandardSchemeFactory();
    private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY =
        new CompleteFileRequestTupleSchemeFactory();

    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
            new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        tmpMap.put(_Fields.OUTPUT_STREAM_ID, new org.apache.thrift.meta_data.FieldMetaData("outputStreamId",
            org.apache.thrift.TFieldRequirementType.REQUIRED,
            new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.BUCKET_NAME,
            new org.apache.thrift.meta_data.FieldMetaData("bucketName",
                org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.PATH,
            new org.apache.thrift.meta_data.FieldMetaData("path", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.AUTH,
            new org.apache.thrift.meta_data.FieldMetaData("auth", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT,
                    Authorization.class)));
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(CompleteFileRequest.class, metaDataMap);
    }

    public @org.apache.thrift.annotation.Nullable
    String outputStreamId; // required
    public @org.apache.thrift.annotation.Nullable
    String bucketName; // required
    public @org.apache.thrift.annotation.Nullable
    String path; // required
    public @org.apache.thrift.annotation.Nullable
    Authorization auth; // required

    public CompleteFileRequest() {
    }

    public CompleteFileRequest(
        String outputStreamId,
        String bucketName,
        String path,
        Authorization auth) {
        this();
        this.outputStreamId = outputStreamId;
        this.bucketName = bucketName;
        this.path = path;
        this.auth = auth;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public CompleteFileRequest(CompleteFileRequest other) {
        if (other.isSetOutputStreamId()) {
            this.outputStreamId = other.outputStreamId;
        }
        if (other.isSetBucketName()) {
            this.bucketName = other.bucketName;
        }
        if (other.isSetPath()) {
            this.path = other.path;
        }
        if (other.isSetAuth()) {
            this.auth = new Authorization(other.auth);
        }
    }

    private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
        return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY :
            TUPLE_SCHEME_FACTORY).getScheme();
    }

    public CompleteFileRequest deepCopy() {
        return new CompleteFileRequest(this);
    }

    @Override
    public void clear() {
        this.outputStreamId = null;
        this.bucketName = null;
        this.path = null;
        this.auth = null;
    }

    @org.apache.thrift.annotation.Nullable
    public String getOutputStreamId() {
        return this.outputStreamId;
    }

    public CompleteFileRequest setOutputStreamId(@org.apache.thrift.annotation.Nullable String outputStreamId) {
        this.outputStreamId = outputStreamId;
        return this;
    }

    public void unsetOutputStreamId() {
        this.outputStreamId = null;
    }

    /**
     * Returns true if field outputStreamId is set (has been assigned a value) and false otherwise
     */
    public boolean isSetOutputStreamId() {
        return this.outputStreamId != null;
    }

    public void setOutputStreamIdIsSet(boolean value) {
        if (!value) {
            this.outputStreamId = null;
        }
    }

    @org.apache.thrift.annotation.Nullable
    public String getBucketName() {
        return this.bucketName;
    }

    public CompleteFileRequest setBucketName(@org.apache.thrift.annotation.Nullable String bucketName) {
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
    public String getPath() {
        return this.path;
    }

    public CompleteFileRequest setPath(@org.apache.thrift.annotation.Nullable String path) {
        this.path = path;
        return this;
    }

    public void unsetPath() {
        this.path = null;
    }

    /**
     * Returns true if field path is set (has been assigned a value) and false otherwise
     */
    public boolean isSetPath() {
        return this.path != null;
    }

    public void setPathIsSet(boolean value) {
        if (!value) {
            this.path = null;
        }
    }

    @org.apache.thrift.annotation.Nullable
    public Authorization getAuth() {
        return this.auth;
    }

    public CompleteFileRequest setAuth(@org.apache.thrift.annotation.Nullable Authorization auth) {
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
        case OUTPUT_STREAM_ID:
            if (value == null) {
                unsetOutputStreamId();
            } else {
                setOutputStreamId((String) value);
            }
            break;

        case BUCKET_NAME:
            if (value == null) {
                unsetBucketName();
            } else {
                setBucketName((String) value);
            }
            break;

        case PATH:
            if (value == null) {
                unsetPath();
            } else {
                setPath((String) value);
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
        case OUTPUT_STREAM_ID:
            return getOutputStreamId();

        case BUCKET_NAME:
            return getBucketName();

        case PATH:
            return getPath();

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
        case OUTPUT_STREAM_ID:
            return isSetOutputStreamId();
        case BUCKET_NAME:
            return isSetBucketName();
        case PATH:
            return isSetPath();
        case AUTH:
            return isSetAuth();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof CompleteFileRequest) {
            return this.equals((CompleteFileRequest) that);
        }
        return false;
    }

    public boolean equals(CompleteFileRequest that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }

        boolean this_present_outputStreamId = true && this.isSetOutputStreamId();
        boolean that_present_outputStreamId = true && that.isSetOutputStreamId();
        if (this_present_outputStreamId || that_present_outputStreamId) {
            if (!(this_present_outputStreamId && that_present_outputStreamId)) {
                return false;
            }
            if (!this.outputStreamId.equals(that.outputStreamId)) {
                return false;
            }
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

        boolean this_present_path = true && this.isSetPath();
        boolean that_present_path = true && that.isSetPath();
        if (this_present_path || that_present_path) {
            if (!(this_present_path && that_present_path)) {
                return false;
            }
            if (!this.path.equals(that.path)) {
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

        hashCode = hashCode * 8191 + ((isSetOutputStreamId()) ? 131071 : 524287);
        if (isSetOutputStreamId()) {
            hashCode = hashCode * 8191 + outputStreamId.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetBucketName()) ? 131071 : 524287);
        if (isSetBucketName()) {
            hashCode = hashCode * 8191 + bucketName.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetPath()) ? 131071 : 524287);
        if (isSetPath()) {
            hashCode = hashCode * 8191 + path.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetAuth()) ? 131071 : 524287);
        if (isSetAuth()) {
            hashCode = hashCode * 8191 + auth.hashCode();
        }

        return hashCode;
    }

    @Override
    public int compareTo(CompleteFileRequest other) {
        if (!getClass().equals(other.getClass())) {
            return getClass().getName().compareTo(other.getClass().getName());
        }

        int lastComparison = 0;

        lastComparison = Boolean.compare(isSetOutputStreamId(), other.isSetOutputStreamId());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetOutputStreamId()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.outputStreamId, other.outputStreamId);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
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
        lastComparison = Boolean.compare(isSetPath(), other.isSetPath());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetPath()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.path, other.path);
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
        StringBuilder sb = new StringBuilder("CompleteFileRequest(");
        boolean first = true;

        sb.append("outputStreamId:");
        if (this.outputStreamId == null) {
            sb.append("null");
        } else {
            sb.append(this.outputStreamId);
        }
        first = false;
        if (!first) {
            sb.append(", ");
        }
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
        sb.append("path:");
        if (this.path == null) {
            sb.append("null");
        } else {
            sb.append(this.path);
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
        if (outputStreamId == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'outputStreamId' was not present! Struct: " + toString());
        }
        if (bucketName == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'bucketName' was not present! Struct: " + toString());
        }
        if (path == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'path' was not present! Struct: " + toString());
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
        OUTPUT_STREAM_ID((short) 1, "outputStreamId"),
        BUCKET_NAME((short) 2, "bucketName"),
        PATH((short) 3, "path"),
        AUTH((short) 4, "auth");

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
            case 1: // OUTPUT_STREAM_ID
                return OUTPUT_STREAM_ID;
            case 2: // BUCKET_NAME
                return BUCKET_NAME;
            case 3: // PATH
                return PATH;
            case 4: // AUTH
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

    private static class CompleteFileRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public CompleteFileRequestStandardScheme getScheme() {
            return new CompleteFileRequestStandardScheme();
        }
    }

    private static class CompleteFileRequestStandardScheme
        extends org.apache.thrift.scheme.StandardScheme<CompleteFileRequest> {

        public void read(org.apache.thrift.protocol.TProtocol iprot, CompleteFileRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TField schemeField;
            iprot.readStructBegin();
            while (true) {
                schemeField = iprot.readFieldBegin();
                if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
                    break;
                }
                switch (schemeField.id) {
                case 1: // OUTPUT_STREAM_ID
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.outputStreamId = iprot.readString();
                        struct.setOutputStreamIdIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 2: // BUCKET_NAME
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.bucketName = iprot.readString();
                        struct.setBucketNameIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 3: // PATH
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.path = iprot.readString();
                        struct.setPathIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 4: // AUTH
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

        public void write(org.apache.thrift.protocol.TProtocol oprot, CompleteFileRequest struct)
            throws org.apache.thrift.TException {
            struct.validate();

            oprot.writeStructBegin(STRUCT_DESC);
            if (struct.outputStreamId != null) {
                oprot.writeFieldBegin(OUTPUT_STREAM_ID_FIELD_DESC);
                oprot.writeString(struct.outputStreamId);
                oprot.writeFieldEnd();
            }
            if (struct.bucketName != null) {
                oprot.writeFieldBegin(BUCKET_NAME_FIELD_DESC);
                oprot.writeString(struct.bucketName);
                oprot.writeFieldEnd();
            }
            if (struct.path != null) {
                oprot.writeFieldBegin(PATH_FIELD_DESC);
                oprot.writeString(struct.path);
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

    private static class CompleteFileRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public CompleteFileRequestTupleScheme getScheme() {
            return new CompleteFileRequestTupleScheme();
        }
    }

    private static class CompleteFileRequestTupleScheme
        extends org.apache.thrift.scheme.TupleScheme<CompleteFileRequest> {

        @Override
        public void write(org.apache.thrift.protocol.TProtocol prot, CompleteFileRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            oprot.writeString(struct.outputStreamId);
            oprot.writeString(struct.bucketName);
            oprot.writeString(struct.path);
            struct.auth.write(oprot);
        }

        @Override
        public void read(org.apache.thrift.protocol.TProtocol prot, CompleteFileRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            struct.outputStreamId = iprot.readString();
            struct.setOutputStreamIdIsSet(true);
            struct.bucketName = iprot.readString();
            struct.setBucketNameIsSet(true);
            struct.path = iprot.readString();
            struct.setPathIsSet(true);
            struct.auth = new Authorization();
            struct.auth.read(iprot);
            struct.setAuthIsSet(true);
        }
    }
}

