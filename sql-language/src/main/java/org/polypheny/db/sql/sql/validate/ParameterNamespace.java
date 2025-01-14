/*
 * Copyright 2019-2021 The Polypheny Project
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
 */

package org.polypheny.db.sql.sql.validate;


import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.sql.sql.SqlNode;


/**
 * Namespace representing the type of a dynamic parameter.
 *
 * @see ParameterScope
 */
class ParameterNamespace extends AbstractNamespace {

    private final AlgDataType type;


    ParameterNamespace( SqlValidatorImpl validator, AlgDataType type ) {
        super( validator, null );
        this.type = type;
    }


    @Override
    public SqlNode getNode() {
        return null;
    }


    @Override
    public AlgDataType validateImpl( AlgDataType targetRowType ) {
        return type;
    }


    @Override
    public AlgDataType getRowType() {
        return type;
    }

}

