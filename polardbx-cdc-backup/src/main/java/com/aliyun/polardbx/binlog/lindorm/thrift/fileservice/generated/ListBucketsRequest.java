/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Autogenerated by Thrift Compiler (0.14.1)
 * <p>
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.aliyun.polardbx.binlog.lindorm.thrift.fileservice.generated;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.1)", date = "2021-05-26")
public class ListBucketsRequest
    implements org.apache.thrift.TBase<ListBucketsRequest, ListBucketsRequest._Fields>, java.io.Serializable, Cloneable,
    Comparable<ListBucketsRequest> {
    // isset id assignments
    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("ListBucketsRequest");
    private static final org.apache.thrift.protocol.TField AUTH_FIELD_DESC =
        new org.apache.thrift.protocol.TField("auth", org.apache.thrift.protocol.TType.STRUCT, (short) 1);
    private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY =
        new ListBucketsRequestStandardSchemeFactory();
    private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY =
        new ListBucketsRequestTupleSchemeFactory();

    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
            new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        tmpMap.put(_Fields.AUTH,
            new org.apache.thrift.meta_data.FieldMetaData("auth", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.StructMetaData(org.apache.thrift.protocol.TType.STRUCT,
                    Authorization.class)));
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(ListBucketsRequest.class, metaDataMap);
    }

    public @org.apache.thrift.annotation.Nullable
    Authorization auth; // required

    public ListBucketsRequest() {
    }

    public ListBucketsRequest(
        Authorization auth) {
        this();
        this.auth = auth;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public ListBucketsRequest(ListBucketsRequest other) {
        if (other.isSetAuth()) {
            this.auth = new Authorization(other.auth);
        }
    }

    private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
        return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY :
            TUPLE_SCHEME_FACTORY).getScheme();
    }

    public ListBucketsRequest deepCopy() {
        return new ListBucketsRequest(this);
    }

    @Override
    public void clear() {
        this.auth = null;
    }

    @org.apache.thrift.annotation.Nullable
    public Authorization getAuth() {
        return this.auth;
    }

    public ListBucketsRequest setAuth(@org.apache.thrift.annotation.Nullable Authorization auth) {
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
        case AUTH:
            return isSetAuth();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof ListBucketsRequest) {
            return this.equals((ListBucketsRequest) that);
        }
        return false;
    }

    public boolean equals(ListBucketsRequest that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
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

        hashCode = hashCode * 8191 + ((isSetAuth()) ? 131071 : 524287);
        if (isSetAuth()) {
            hashCode = hashCode * 8191 + auth.hashCode();
        }

        return hashCode;
    }

    @Override
    public int compareTo(ListBucketsRequest other) {
        if (!getClass().equals(other.getClass())) {
            return getClass().getName().compareTo(other.getClass().getName());
        }

        int lastComparison = 0;

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
        StringBuilder sb = new StringBuilder("ListBucketsRequest(");
        boolean first = true;

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
        AUTH((short) 1, "auth");

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
            case 1: // AUTH
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

    private static class ListBucketsRequestStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public ListBucketsRequestStandardScheme getScheme() {
            return new ListBucketsRequestStandardScheme();
        }
    }

    private static class ListBucketsRequestStandardScheme
        extends org.apache.thrift.scheme.StandardScheme<ListBucketsRequest> {

        public void read(org.apache.thrift.protocol.TProtocol iprot, ListBucketsRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TField schemeField;
            iprot.readStructBegin();
            while (true) {
                schemeField = iprot.readFieldBegin();
                if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
                    break;
                }
                switch (schemeField.id) {
                case 1: // AUTH
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

        public void write(org.apache.thrift.protocol.TProtocol oprot, ListBucketsRequest struct)
            throws org.apache.thrift.TException {
            struct.validate();

            oprot.writeStructBegin(STRUCT_DESC);
            if (struct.auth != null) {
                oprot.writeFieldBegin(AUTH_FIELD_DESC);
                struct.auth.write(oprot);
                oprot.writeFieldEnd();
            }
            oprot.writeFieldStop();
            oprot.writeStructEnd();
        }

    }

    private static class ListBucketsRequestTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public ListBucketsRequestTupleScheme getScheme() {
            return new ListBucketsRequestTupleScheme();
        }
    }

    private static class ListBucketsRequestTupleScheme
        extends org.apache.thrift.scheme.TupleScheme<ListBucketsRequest> {

        @Override
        public void write(org.apache.thrift.protocol.TProtocol prot, ListBucketsRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            struct.auth.write(oprot);
        }

        @Override
        public void read(org.apache.thrift.protocol.TProtocol prot, ListBucketsRequest struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            struct.auth = new Authorization();
            struct.auth.read(iprot);
            struct.setAuthIsSet(true);
        }
    }
}
