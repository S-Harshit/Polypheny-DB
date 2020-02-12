/*
 * Copyright 2019-2020 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.rel.type;


import com.google.common.collect.ImmutableList;
import java.util.List;
import org.polypheny.db.sql.type.SqlTypeExplicitPrecedenceList;
import org.polypheny.db.sql.type.SqlTypeName;
import org.polypheny.db.util.Pair;


/**
 * Implementation of {@link RelDataType} for a dynamic table.
 *
 * It's used during SQL validation, where the field list is mutable for the getField() call. After SQL validation, a normal {@link RelDataTypeImpl} with an immutable field list takes the place
 * of the DynamicRecordTypeImpl instance.
 */
public class DynamicRecordTypeImpl extends DynamicRecordType {

    private final RelDataTypeHolder holder;


    /**
     * Creates a DynamicRecordTypeImpl.
     */
    public DynamicRecordTypeImpl( RelDataTypeFactory typeFactory ) {
        this.holder = new RelDataTypeHolder( typeFactory );
        computeDigest();
    }


    @Override
    public List<RelDataTypeField> getFieldList() {
        return holder.getFieldList();
    }


    @Override
    public int getFieldCount() {
        return holder.getFieldCount();
    }


    @Override
    public RelDataTypeField getField( String fieldName, boolean caseSensitive, boolean elideRecord ) {
        final Pair<RelDataTypeField, Boolean> pair = holder.getFieldOrInsert( fieldName, caseSensitive );
        // If a new field is added, we should re-compute the digest.
        if ( pair.right ) {
            computeDigest();
        }

        return pair.left;
    }


    @Override
    public List<String> getFieldNames() {
        return holder.getFieldNames();
    }


    @Override
    public SqlTypeName getSqlTypeName() {
        return SqlTypeName.ROW;
    }


    @Override
    public RelDataTypePrecedenceList getPrecedenceList() {
        return new SqlTypeExplicitPrecedenceList( ImmutableList.of() );
    }


    @Override
    protected void generateTypeString( StringBuilder sb, boolean withDetail ) {
        sb.append( "(DynamicRecordRow" ).append( getFieldNames() ).append( ")" );
    }


    @Override
    public boolean isStruct() {
        return true;
    }


    @Override
    public RelDataTypeFamily getFamily() {
        return getSqlTypeName().getFamily();
    }

}
