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

package org.polypheny.db.sql.util;


import org.polypheny.db.sql.SqlCall;
import org.polypheny.db.sql.SqlDataTypeSpec;
import org.polypheny.db.sql.SqlDynamicParam;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlIntervalQualifier;
import org.polypheny.db.sql.SqlLiteral;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlNodeList;


/**
 * Basic implementation of {@link SqlVisitor} which does nothing at each node.
 *
 * This class is useful as a base class for classes which implement the {@link SqlVisitor} interface. The derived class can override whichever methods it chooses.
 *
 * @param <R> Return type
 */
public class SqlBasicVisitor<R> implements SqlVisitor<R> {

    @Override
    public R visit( SqlLiteral literal ) {
        return null;
    }


    @Override
    public R visit( SqlCall call ) {
        return call.getOperator().acceptCall( this, call );
    }


    @Override
    public R visit( SqlNodeList nodeList ) {
        R result = null;
        for ( int i = 0; i < nodeList.size(); i++ ) {
            SqlNode node = nodeList.get( i );
            result = node.accept( this );
        }
        return result;
    }


    @Override
    public R visit( SqlIdentifier id ) {
        return null;
    }


    @Override
    public R visit( SqlDataTypeSpec type ) {
        return null;
    }


    @Override
    public R visit( SqlDynamicParam param ) {
        return null;
    }


    @Override
    public R visit( SqlIntervalQualifier intervalQualifier ) {
        return null;
    }


    /**
     * Argument handler.
     *
     * @param <R> result type
     */
    public interface ArgHandler<R> {

        /**
         * Returns the result of visiting all children of a call to an operator, then the call itself.
         *
         * Typically the result will be the result of the last child visited, or (if R is {@link Boolean}) whether all children were visited successfully.
         */
        R result();

        /**
         * Visits a particular operand of a call, using a given visitor.
         */
        R visitChild( SqlVisitor<R> visitor, SqlNode expr, int i, SqlNode operand );
    }


    /**
     * Default implementation of {@link ArgHandler} which merely calls {@link SqlNode#accept} on each operand.
     *
     * @param <R> result type
     */
    public static class ArgHandlerImpl<R> implements ArgHandler<R> {

        private static final ArgHandler INSTANCE = new ArgHandlerImpl();


        @SuppressWarnings("unchecked")
        public static <R> ArgHandler<R> instance() {
            return INSTANCE;
        }


        @Override
        public R result() {
            return null;
        }


        @Override
        public R visitChild( SqlVisitor<R> visitor, SqlNode expr, int i, SqlNode operand ) {
            if ( operand == null ) {
                return null;
            }
            return operand.accept( visitor );
        }
    }
}

