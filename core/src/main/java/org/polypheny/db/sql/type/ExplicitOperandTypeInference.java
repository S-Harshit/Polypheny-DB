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
import com.google.common.collect.ImmutableList;


/**
 * ExplicitOperandTypeInferences implements {@link SqlOperandTypeInference} by explicitly supplying a type for each parameter.
 */
public class ExplicitOperandTypeInference implements SqlOperandTypeInference {

    private final ImmutableList<RelDataType> paramTypes;


    /**
     * Use {@link org.polypheny.db.sql.type.InferTypes#explicit(java.util.List)}.
     */
    ExplicitOperandTypeInference( ImmutableList<RelDataType> paramTypes ) {
        this.paramTypes = paramTypes;
    }


    @Override
    public void inferOperandTypes( SqlCallBinding callBinding, RelDataType returnType, RelDataType[] operandTypes ) {
        if ( operandTypes.length != paramTypes.size() ) {
            // This call does not match the inference strategy.
            // It's likely that we're just about to give a validation error.
            // Don't make a fuss, just give up.
            return;
        }
        paramTypes.toArray( operandTypes );
    }
}

