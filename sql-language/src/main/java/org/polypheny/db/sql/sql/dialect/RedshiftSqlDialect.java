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

package org.polypheny.db.sql.sql.dialect;


import org.polypheny.db.sql.sql.SqlDialect;
import org.polypheny.db.sql.sql.SqlNode;
import org.polypheny.db.sql.sql.SqlWriter;


/**
 * A <code>SqlDialect</code> implementation for the Redshift database.
 */
public class RedshiftSqlDialect extends SqlDialect {

    public static final SqlDialect DEFAULT =
            new RedshiftSqlDialect( EMPTY_CONTEXT
                    .withDatabaseProduct( DatabaseProduct.REDSHIFT )
                    .withIdentifierQuoteString( "\"" ) );


    /**
     * Creates a RedshiftSqlDialect.
     */
    public RedshiftSqlDialect( Context context ) {
        super( context );
    }


    @Override
    public void unparseOffsetFetch( SqlWriter writer, SqlNode offset, SqlNode fetch ) {
        unparseFetchUsingLimit( writer, offset, fetch );
    }

}

