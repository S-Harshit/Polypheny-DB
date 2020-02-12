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
 */

package org.polypheny.db.catalog.entity;


import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;


/**
 *
 */
@EqualsAndHashCode(callSuper = true)
public final class CatalogPrimaryKey extends CatalogKey {


    public CatalogPrimaryKey(
            final long id,
            final long tableId,
            @NonNull final String tableName,
            final long schemaId,
            @NonNull final String schemaName,
            final long databaseId,
            @NonNull final String databaseName ) {
        super( id, tableId, tableName, schemaId, schemaName, databaseId, databaseName );
    }


    public CatalogPrimaryKey( @NonNull final CatalogKey catalogKey ) {
        super(
                catalogKey.id,
                catalogKey.tableId,
                catalogKey.tableName,
                catalogKey.schemaId,
                catalogKey.schemaName,
                catalogKey.databaseId,
                catalogKey.databaseName,
                catalogKey.columnIds,
                catalogKey.columnNames );
    }


    // Used for creating ResultSets
    public List<CatalogPrimaryKeyColumn> getCatalogPrimaryKeyColumns() {
        int i = 1;
        LinkedList<CatalogPrimaryKeyColumn> list = new LinkedList<>();
        for ( String columnName : columnNames ) {
            list.add( new CatalogPrimaryKeyColumn( i++, columnName ) );
        }
        return list;
    }


    // Used for creating ResultSets
    @RequiredArgsConstructor
    public class CatalogPrimaryKeyColumn implements CatalogEntity {

        private static final long serialVersionUID = 5426944084650275437L;

        private final int keySeq;
        private final String columnName;


        @Override
        public Serializable[] getParameterArray() {
            return new Serializable[]{ databaseName, schemaName, tableName, columnName, keySeq, null };
        }


        @RequiredArgsConstructor
        public class PrimitiveCatalogPrimaryKeyColumn {

            public final String tableCat;
            public final String tableSchem;
            public final String tableName;
            public final String columnName;
            public final int keySeq;
            public final String pkName;
        }

    }


}
