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

package org.polypheny.db.sql.sql.parser;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.sql.sql.SqlNode;


/**
 * Builder for {@link ParserPos}.
 *
 * Because it is mutable, it is convenient for keeping track of the positions of the tokens that go into a non-terminal. It can be passed into methods, which can add the positions of tokens consumed to it.
 *
 * Some patterns:
 *
 * <ul>
 * <li>{@code final Span s;} declaration of a Span at the top of a production</li>
 * <li>{@code s = span();} initializes s to a Span that includes the token we just saw; very often occurs immediately after the first token in the production</li>
 * <li>{@code s.end(this);} adds the most recent token to span s and evaluates to a SqlParserPosition that spans from beginning to end; commonly used when making a call to a function</li>
 * <li>{@code s.pos()} returns a position spanning all tokens in the list</li>
 * <li>{@code s.add(node);} adds a SqlNode's parser position to a span</li>
 * <li>{@code s.addAll(nodeList);} adds several SqlNodes' parser positions to a span</li>
 * <li>{@code s = Span.of();} initializes s to an empty Span, not even including the most recent token; rarely used</li>
 * </ul>
 */
public final class Span {

    private final List<ParserPos> posList = new ArrayList<>();


    /**
     * Use one of the {@link #of} methods.
     */
    private Span() {
    }


    /**
     * Creates an empty Span.
     */
    public static Span of() {
        return new Span();
    }


    /**
     * Creates a Span with one position.
     */
    public static Span of( ParserPos p ) {
        return new Span().add( p );
    }


    /**
     * Creates a Span of one node.
     */
    public static Span of( SqlNode n ) {
        return new Span().add( n );
    }


    /**
     * Creates a Span between two nodes.
     */
    public static Span of( SqlNode n0, SqlNode n1 ) {
        return new Span().add( n0 ).add( n1 );
    }


    /**
     * Creates a Span of a list of nodes.
     */
    public static Span of( Collection<? extends SqlNode> nodes ) {
        return new Span().addAll( nodes );
    }


    /**
     * Adds a node's position to the list, and returns this Span.
     */
    public Span add( SqlNode n ) {
        return add( n.getPos() );
    }


    /**
     * Adds a node's position to the list if the node is not null, and returns this Span.
     */
    public Span addIf( SqlNode n ) {
        return n == null ? this : add( n );
    }


    /**
     * Adds a position to the list, and returns this Span.
     */
    public Span add( ParserPos pos ) {
        posList.add( pos );
        return this;
    }


    /**
     * Adds the positions of a collection of nodes to the list, and returns this Span.
     */
    public Span addAll( Iterable<? extends SqlNode> nodes ) {
        for ( SqlNode node : nodes ) {
            add( node );
        }
        return this;
    }


    /**
     * Adds the position of the last token emitted by a parser to the list, and returns this Span.
     */
    public Span add( SqlAbstractParserImpl parser ) {
        try {
            final ParserPos pos = parser.getPos();
            return add( pos );
        } catch ( Exception e ) {
            // getPos does not really throw an exception
            throw new AssertionError( e );
        }
    }


    /**
     * Returns a position spanning the earliest position to the latest.
     * Does not assume that the positions are sorted.
     * Throws if the list is empty.
     */
    public ParserPos pos() {
        switch ( posList.size() ) {
            case 0:
                throw new AssertionError();
            case 1:
                return posList.get( 0 );
            default:
                return ParserPos.sum( posList );
        }
    }


    /**
     * Adds the position of the last token emitted by a parser to the list, and returns a position that covers the whole range.
     */
    public ParserPos end( SqlAbstractParserImpl parser ) {
        return add( parser ).pos();
    }


    /**
     * Adds a node's position to the list, and returns a position that covers the whole range.
     */
    public ParserPos end( SqlNode n ) {
        return add( n ).pos();
    }


    /**
     * Clears the contents of this Span, and returns this Span.
     */
    public Span clear() {
        posList.clear();
        return this;
    }

}

