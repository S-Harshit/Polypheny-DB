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

package org.polypheny.db.sql.validate;


import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.schema.Function;
import org.polypheny.db.schema.FunctionParameter;
import org.polypheny.db.sql.SqlFunction;
import org.polypheny.db.sql.SqlFunctionCategory;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.type.SqlOperandTypeChecker;
import org.polypheny.db.sql.type.SqlOperandTypeInference;
import org.polypheny.db.sql.type.SqlReturnTypeInference;
import org.polypheny.db.util.Util;
import com.google.common.collect.Lists;
import java.util.List;


/**
 * User-defined scalar function.
 *
 * Created by the validator, after resolving a function call to a function defined in a Polypheny-DB schema.
 */
public class SqlUserDefinedFunction extends SqlFunction {

    public final Function function;


    /**
     * Creates a {@link SqlUserDefinedFunction}.
     */
    public SqlUserDefinedFunction( SqlIdentifier opName, SqlReturnTypeInference returnTypeInference, SqlOperandTypeInference operandTypeInference, SqlOperandTypeChecker operandTypeChecker, List<RelDataType> paramTypes, Function function ) {
        this(
                opName,
                returnTypeInference,
                operandTypeInference,
                operandTypeChecker,
                paramTypes,
                function,
                SqlFunctionCategory.USER_DEFINED_FUNCTION );
    }


    /**
     * Constructor used internally and by derived classes.
     */
    protected SqlUserDefinedFunction( SqlIdentifier opName, SqlReturnTypeInference returnTypeInference, SqlOperandTypeInference operandTypeInference, SqlOperandTypeChecker operandTypeChecker, List<RelDataType> paramTypes, Function function, SqlFunctionCategory category ) {
        super(
                Util.last( opName.names ),
                opName,
                SqlKind.OTHER_FUNCTION,
                returnTypeInference,
                operandTypeInference,
                operandTypeChecker,
                paramTypes,
                category );
        this.function = function;
    }


    /**
     * Returns function that implements given operator call.
     *
     * @return function that implements given operator call
     */
    public Function getFunction() {
        return function;
    }


    @Override
    public List<String> getParamNames() {
        return Lists.transform( function.getParameters(), FunctionParameter::getName );
    }
}

