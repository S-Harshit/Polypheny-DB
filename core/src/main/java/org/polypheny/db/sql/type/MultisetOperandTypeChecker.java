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

package org.polypheny.db.sql.type;


import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.sql.SqlCallBinding;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlOperandCountRange;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.util.Static;
import com.google.common.collect.ImmutableList;


/**
 * Parameter type-checking strategy types must be [nullable] Multiset, [nullable] Multiset and the two types must have the same element type
 *
 * @see MultisetSqlType#getComponentType
 */
public class MultisetOperandTypeChecker implements SqlOperandTypeChecker {


    @Override
    public boolean isOptional( int i ) {
        return false;
    }


    @Override
    public boolean checkOperandTypes( SqlCallBinding callBinding, boolean throwOnFailure ) {
        final SqlNode op0 = callBinding.operand( 0 );
        if ( !OperandTypes.MULTISET.checkSingleOperandType( callBinding, op0, 0, throwOnFailure ) ) {
            return false;
        }

        final SqlNode op1 = callBinding.operand( 1 );
        if ( !OperandTypes.MULTISET.checkSingleOperandType( callBinding, op1, 0, throwOnFailure ) ) {
            return false;
        }

        // TODO: this won't work if element types are of ROW types and there is a mismatch.
        RelDataType biggest =
                callBinding.getTypeFactory().leastRestrictive(
                        ImmutableList.of(
                                callBinding.getValidator()
                                        .deriveType( callBinding.getScope(), op0 )
                                        .getComponentType(),
                                callBinding.getValidator()
                                        .deriveType( callBinding.getScope(), op1 )
                                        .getComponentType() ) );
        if ( null == biggest ) {
            if ( throwOnFailure ) {
                throw callBinding.newError(
                        Static.RESOURCE.typeNotComparable(
                                op0.getParserPosition().toString(),
                                op1.getParserPosition().toString() ) );
            }

            return false;
        }
        return true;
    }


    @Override
    public SqlOperandCountRange getOperandCountRange() {
        return SqlOperandCountRanges.of( 2 );
    }


    @Override
    public String getAllowedSignatures( SqlOperator op, String opName ) {
        return "<MULTISET> " + opName + " <MULTISET>";
    }


    @Override
    public Consistency getConsistency() {
        return Consistency.NONE;
    }
}

