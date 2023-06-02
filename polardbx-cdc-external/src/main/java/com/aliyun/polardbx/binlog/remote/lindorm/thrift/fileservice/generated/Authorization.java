/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
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
package com.aliyun.polardbx.binlog.remote.lindorm.thrift.fileservice.generated;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.14.1)", date = "2021-05-26")
public class Authorization
    implements org.apache.thrift.TBase<Authorization, Authorization._Fields>, java.io.Serializable, Cloneable,
    Comparable<Authorization> {
    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
        new org.apache.thrift.protocol.TStruct("Authorization");
    private static final org.apache.thrift.protocol.TField ACCESS_KEY_ID_FIELD_DESC =
        new org.apache.thrift.protocol.TField("accessKeyID", org.apache.thrift.protocol.TType.STRING, (short) 1);
    private static final org.apache.thrift.protocol.TField SIGNATURE_FIELD_DESC =
        new org.apache.thrift.protocol.TField("signature", org.apache.thrift.protocol.TType.STRING, (short) 2);
    private static final org.apache.thrift.protocol.TField TIMESTAMP_FIELD_DESC =
        new org.apache.thrift.protocol.TField("timestamp", org.apache.thrift.protocol.TType.I64, (short) 3);
    private static final org.apache.thrift.protocol.TField STS_TOKEN_FIELD_DESC =
        new org.apache.thrift.protocol.TField("stsToken", org.apache.thrift.protocol.TType.STRING, (short) 4);
    private static final org.apache.thrift.scheme.SchemeFactory STANDARD_SCHEME_FACTORY =
        new AuthorizationStandardSchemeFactory();
    private static final org.apache.thrift.scheme.SchemeFactory TUPLE_SCHEME_FACTORY =
        new AuthorizationTupleSchemeFactory();
    // isset id assignments
    private static final int __TIMESTAMP_ISSET_ID = 0;
    private static final _Fields optionals[] = {_Fields.STS_TOKEN};

    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
            new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        tmpMap.put(_Fields.ACCESS_KEY_ID,
            new org.apache.thrift.meta_data.FieldMetaData("accessKeyID",
                org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.SIGNATURE,
            new org.apache.thrift.meta_data.FieldMetaData("signature", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        tmpMap.put(_Fields.TIMESTAMP,
            new org.apache.thrift.meta_data.FieldMetaData("timestamp", org.apache.thrift.TFieldRequirementType.REQUIRED,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I64)));
        tmpMap.put(_Fields.STS_TOKEN,
            new org.apache.thrift.meta_data.FieldMetaData("stsToken", org.apache.thrift.TFieldRequirementType.OPTIONAL,
                new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(Authorization.class, metaDataMap);
    }

    public @org.apache.thrift.annotation.Nullable
    String accessKeyID; // required
    public @org.apache.thrift.annotation.Nullable
    String signature; // required
    public long timestamp; // required
    public @org.apache.thrift.annotation.Nullable
    String stsToken; // optional
    private byte __isset_bitfield = 0;

    public Authorization() {
    }

    public Authorization(
        String accessKeyID,
        String signature,
        long timestamp) {
        this();
        this.accessKeyID = accessKeyID;
        this.signature = signature;
        this.timestamp = timestamp;
        setTimestampIsSet(true);
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public Authorization(Authorization other) {
        __isset_bitfield = other.__isset_bitfield;
        if (other.isSetAccessKeyID()) {
            this.accessKeyID = other.accessKeyID;
        }
        if (other.isSetSignature()) {
            this.signature = other.signature;
        }
        this.timestamp = other.timestamp;
        if (other.isSetStsToken()) {
            this.stsToken = other.stsToken;
        }
    }

    private static <S extends org.apache.thrift.scheme.IScheme> S scheme(org.apache.thrift.protocol.TProtocol proto) {
        return (org.apache.thrift.scheme.StandardScheme.class.equals(proto.getScheme()) ? STANDARD_SCHEME_FACTORY :
            TUPLE_SCHEME_FACTORY).getScheme();
    }

    public Authorization deepCopy() {
        return new Authorization(this);
    }

    @Override
    public void clear() {
        this.accessKeyID = null;
        this.signature = null;
        setTimestampIsSet(false);
        this.timestamp = 0;
        this.stsToken = null;
    }

    @org.apache.thrift.annotation.Nullable
    public String getAccessKeyID() {
        return this.accessKeyID;
    }

    public Authorization setAccessKeyID(@org.apache.thrift.annotation.Nullable String accessKeyID) {
        this.accessKeyID = accessKeyID;
        return this;
    }

    public void unsetAccessKeyID() {
        this.accessKeyID = null;
    }

    /**
     * Returns true if field accessKeyID is set (has been assigned a value) and false otherwise
     */
    public boolean isSetAccessKeyID() {
        return this.accessKeyID != null;
    }

    public void setAccessKeyIDIsSet(boolean value) {
        if (!value) {
            this.accessKeyID = null;
        }
    }

    @org.apache.thrift.annotation.Nullable
    public String getSignature() {
        return this.signature;
    }

    public Authorization setSignature(@org.apache.thrift.annotation.Nullable String signature) {
        this.signature = signature;
        return this;
    }

    public void unsetSignature() {
        this.signature = null;
    }

    /**
     * Returns true if field signature is set (has been assigned a value) and false otherwise
     */
    public boolean isSetSignature() {
        return this.signature != null;
    }

    public void setSignatureIsSet(boolean value) {
        if (!value) {
            this.signature = null;
        }
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public Authorization setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        setTimestampIsSet(true);
        return this;
    }

    public void unsetTimestamp() {
        __isset_bitfield = org.apache.thrift.EncodingUtils.clearBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
    }

    /**
     * Returns true if field timestamp is set (has been assigned a value) and false otherwise
     */
    public boolean isSetTimestamp() {
        return org.apache.thrift.EncodingUtils.testBit(__isset_bitfield, __TIMESTAMP_ISSET_ID);
    }

    public void setTimestampIsSet(boolean value) {
        __isset_bitfield = org.apache.thrift.EncodingUtils.setBit(__isset_bitfield, __TIMESTAMP_ISSET_ID, value);
    }

    @org.apache.thrift.annotation.Nullable
    public String getStsToken() {
        return this.stsToken;
    }

    public Authorization setStsToken(@org.apache.thrift.annotation.Nullable String stsToken) {
        this.stsToken = stsToken;
        return this;
    }

    public void unsetStsToken() {
        this.stsToken = null;
    }

    /**
     * Returns true if field stsToken is set (has been assigned a value) and false otherwise
     */
    public boolean isSetStsToken() {
        return this.stsToken != null;
    }

    public void setStsTokenIsSet(boolean value) {
        if (!value) {
            this.stsToken = null;
        }
    }

    public void setFieldValue(_Fields field, @org.apache.thrift.annotation.Nullable Object value) {
        switch (field) {
        case ACCESS_KEY_ID:
            if (value == null) {
                unsetAccessKeyID();
            } else {
                setAccessKeyID((String) value);
            }
            break;

        case SIGNATURE:
            if (value == null) {
                unsetSignature();
            } else {
                setSignature((String) value);
            }
            break;

        case TIMESTAMP:
            if (value == null) {
                unsetTimestamp();
            } else {
                setTimestamp((Long) value);
            }
            break;

        case STS_TOKEN:
            if (value == null) {
                unsetStsToken();
            } else {
                setStsToken((String) value);
            }
            break;

        }
    }

    @org.apache.thrift.annotation.Nullable
    public Object getFieldValue(_Fields field) {
        switch (field) {
        case ACCESS_KEY_ID:
            return getAccessKeyID();

        case SIGNATURE:
            return getSignature();

        case TIMESTAMP:
            return getTimestamp();

        case STS_TOKEN:
            return getStsToken();

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
        case ACCESS_KEY_ID:
            return isSetAccessKeyID();
        case SIGNATURE:
            return isSetSignature();
        case TIMESTAMP:
            return isSetTimestamp();
        case STS_TOKEN:
            return isSetStsToken();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean equals(Object that) {
        if (that instanceof Authorization) {
            return this.equals((Authorization) that);
        }
        return false;
    }

    public boolean equals(Authorization that) {
        if (that == null) {
            return false;
        }
        if (this == that) {
            return true;
        }

        boolean this_present_accessKeyID = true && this.isSetAccessKeyID();
        boolean that_present_accessKeyID = true && that.isSetAccessKeyID();
        if (this_present_accessKeyID || that_present_accessKeyID) {
            if (!(this_present_accessKeyID && that_present_accessKeyID)) {
                return false;
            }
            if (!this.accessKeyID.equals(that.accessKeyID)) {
                return false;
            }
        }

        boolean this_present_signature = true && this.isSetSignature();
        boolean that_present_signature = true && that.isSetSignature();
        if (this_present_signature || that_present_signature) {
            if (!(this_present_signature && that_present_signature)) {
                return false;
            }
            if (!this.signature.equals(that.signature)) {
                return false;
            }
        }

        boolean this_present_timestamp = true;
        boolean that_present_timestamp = true;
        if (this_present_timestamp || that_present_timestamp) {
            if (!(this_present_timestamp && that_present_timestamp)) {
                return false;
            }
            if (this.timestamp != that.timestamp) {
                return false;
            }
        }

        boolean this_present_stsToken = true && this.isSetStsToken();
        boolean that_present_stsToken = true && that.isSetStsToken();
        if (this_present_stsToken || that_present_stsToken) {
            if (!(this_present_stsToken && that_present_stsToken)) {
                return false;
            }
            if (!this.stsToken.equals(that.stsToken)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = 1;

        hashCode = hashCode * 8191 + ((isSetAccessKeyID()) ? 131071 : 524287);
        if (isSetAccessKeyID()) {
            hashCode = hashCode * 8191 + accessKeyID.hashCode();
        }

        hashCode = hashCode * 8191 + ((isSetSignature()) ? 131071 : 524287);
        if (isSetSignature()) {
            hashCode = hashCode * 8191 + signature.hashCode();
        }

        hashCode = hashCode * 8191 + org.apache.thrift.TBaseHelper.hashCode(timestamp);

        hashCode = hashCode * 8191 + ((isSetStsToken()) ? 131071 : 524287);
        if (isSetStsToken()) {
            hashCode = hashCode * 8191 + stsToken.hashCode();
        }

        return hashCode;
    }

    @Override
    public int compareTo(Authorization other) {
        if (!getClass().equals(other.getClass())) {
            return getClass().getName().compareTo(other.getClass().getName());
        }

        int lastComparison = 0;

        lastComparison = Boolean.compare(isSetAccessKeyID(), other.isSetAccessKeyID());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetAccessKeyID()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.accessKeyID, other.accessKeyID);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = Boolean.compare(isSetSignature(), other.isSetSignature());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetSignature()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.signature, other.signature);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = Boolean.compare(isSetTimestamp(), other.isSetTimestamp());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetTimestamp()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.timestamp, other.timestamp);
            if (lastComparison != 0) {
                return lastComparison;
            }
        }
        lastComparison = Boolean.compare(isSetStsToken(), other.isSetStsToken());
        if (lastComparison != 0) {
            return lastComparison;
        }
        if (isSetStsToken()) {
            lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.stsToken, other.stsToken);
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
        StringBuilder sb = new StringBuilder("Authorization(");
        boolean first = true;

        sb.append("accessKeyID:");
        if (this.accessKeyID == null) {
            sb.append("null");
        } else {
            sb.append(this.accessKeyID);
        }
        first = false;
        if (!first) {
            sb.append(", ");
        }
        sb.append("signature:");
        if (this.signature == null) {
            sb.append("null");
        } else {
            sb.append(this.signature);
        }
        first = false;
        if (!first) {
            sb.append(", ");
        }
        sb.append("timestamp:");
        sb.append(this.timestamp);
        first = false;
        if (isSetStsToken()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("stsToken:");
            if (this.stsToken == null) {
                sb.append("null");
            } else {
                sb.append(this.stsToken);
            }
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    public void validate() throws org.apache.thrift.TException {
        // check for required fields
        if (accessKeyID == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'accessKeyID' was not present! Struct: " + toString());
        }
        if (signature == null) {
            throw new org.apache.thrift.protocol.TProtocolException(
                "Required field 'signature' was not present! Struct: " + toString());
        }
        // alas, we cannot check 'timestamp' because it's a primitive and you chose the non-beans generator.
        // check for sub-struct validity
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
            // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
            __isset_bitfield = 0;
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
        ACCESS_KEY_ID((short) 1, "accessKeyID"),
        SIGNATURE((short) 2, "signature"),
        TIMESTAMP((short) 3, "timestamp"),
        STS_TOKEN((short) 4, "stsToken");

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
            case 1: // ACCESS_KEY_ID
                return ACCESS_KEY_ID;
            case 2: // SIGNATURE
                return SIGNATURE;
            case 3: // TIMESTAMP
                return TIMESTAMP;
            case 4: // STS_TOKEN
                return STS_TOKEN;
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

    private static class AuthorizationStandardSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public AuthorizationStandardScheme getScheme() {
            return new AuthorizationStandardScheme();
        }
    }

    private static class AuthorizationStandardScheme extends org.apache.thrift.scheme.StandardScheme<Authorization> {

        public void read(org.apache.thrift.protocol.TProtocol iprot, Authorization struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TField schemeField;
            iprot.readStructBegin();
            while (true) {
                schemeField = iprot.readFieldBegin();
                if (schemeField.type == org.apache.thrift.protocol.TType.STOP) {
                    break;
                }
                switch (schemeField.id) {
                case 1: // ACCESS_KEY_ID
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.accessKeyID = iprot.readString();
                        struct.setAccessKeyIDIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 2: // SIGNATURE
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.signature = iprot.readString();
                        struct.setSignatureIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 3: // TIMESTAMP
                    if (schemeField.type == org.apache.thrift.protocol.TType.I64) {
                        struct.timestamp = iprot.readI64();
                        struct.setTimestampIsSet(true);
                    } else {
                        org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
                    }
                    break;
                case 4: // STS_TOKEN
                    if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
                        struct.stsToken = iprot.readString();
                        struct.setStsTokenIsSet(true);
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
            if (!struct.isSetTimestamp()) {
                throw new org.apache.thrift.protocol.TProtocolException(
                    "Required field 'timestamp' was not found in serialized data! Struct: " + toString());
            }
            struct.validate();
        }

        public void write(org.apache.thrift.protocol.TProtocol oprot, Authorization struct)
            throws org.apache.thrift.TException {
            struct.validate();

            oprot.writeStructBegin(STRUCT_DESC);
            if (struct.accessKeyID != null) {
                oprot.writeFieldBegin(ACCESS_KEY_ID_FIELD_DESC);
                oprot.writeString(struct.accessKeyID);
                oprot.writeFieldEnd();
            }
            if (struct.signature != null) {
                oprot.writeFieldBegin(SIGNATURE_FIELD_DESC);
                oprot.writeString(struct.signature);
                oprot.writeFieldEnd();
            }
            oprot.writeFieldBegin(TIMESTAMP_FIELD_DESC);
            oprot.writeI64(struct.timestamp);
            oprot.writeFieldEnd();
            if (struct.stsToken != null) {
                if (struct.isSetStsToken()) {
                    oprot.writeFieldBegin(STS_TOKEN_FIELD_DESC);
                    oprot.writeString(struct.stsToken);
                    oprot.writeFieldEnd();
                }
            }
            oprot.writeFieldStop();
            oprot.writeStructEnd();
        }

    }

    private static class AuthorizationTupleSchemeFactory implements org.apache.thrift.scheme.SchemeFactory {
        public AuthorizationTupleScheme getScheme() {
            return new AuthorizationTupleScheme();
        }
    }

    private static class AuthorizationTupleScheme extends org.apache.thrift.scheme.TupleScheme<Authorization> {

        @Override
        public void write(org.apache.thrift.protocol.TProtocol prot, Authorization struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol oprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            oprot.writeString(struct.accessKeyID);
            oprot.writeString(struct.signature);
            oprot.writeI64(struct.timestamp);
            java.util.BitSet optionals = new java.util.BitSet();
            if (struct.isSetStsToken()) {
                optionals.set(0);
            }
            oprot.writeBitSet(optionals, 1);
            if (struct.isSetStsToken()) {
                oprot.writeString(struct.stsToken);
            }
        }

        @Override
        public void read(org.apache.thrift.protocol.TProtocol prot, Authorization struct)
            throws org.apache.thrift.TException {
            org.apache.thrift.protocol.TTupleProtocol iprot = (org.apache.thrift.protocol.TTupleProtocol) prot;
            struct.accessKeyID = iprot.readString();
            struct.setAccessKeyIDIsSet(true);
            struct.signature = iprot.readString();
            struct.setSignatureIsSet(true);
            struct.timestamp = iprot.readI64();
            struct.setTimestampIsSet(true);
            java.util.BitSet incoming = iprot.readBitSet(1);
            if (incoming.get(0)) {
                struct.stsToken = iprot.readString();
                struct.setStsTokenIsSet(true);
            }
        }
    }
}
