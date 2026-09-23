<?xml version="1.0" encoding="utf-8"?>
<!--
  Copyright (c) 2026 Contributors to the Eclipse Foundation.

  This program and the accompanying materials are made
  available under the terms of the Eclipse Public License 2.0
  which is available at https://www.eclipse.org/legal/epl-2.0/
  SPDX-License-Identifier: EPL-2.0

  Contributors:
-->
<!--
  The DocBook 1.78.1 chunker writes files with saxon:output (Saxon 6),
  exsl:document or Xalan redirect:write, none of which Saxon-HE supports.
  xsl:result-document cannot replace them directly: DocBook writes the chunks
  of a book while it is still building the content of the book chunk, and
  XSLT 3.0 forbids writing a result document from inside a variable or
  parameter.

  So chunking is done in two stages. write.chunk emits an osgi:chunk marker
  element carrying the file name and serialization settings, and chunks nest
  where DocBook would have written them. The root template then collects the
  markers and writes each one with xsl:result-document, leaving out the
  chunks nested inside it.

  The module is XSLT 3.0 so that these instructions are available, while the
  imported DocBook modules keep running in backwards compatible mode.
-->
<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:osgi="urn:org.osgi:docbook:chunk"
  exclude-result-prefixes="osgi"
  version="3.0">

<xsl:template match="/" priority="100">
  <xsl:param name="osgi.collecting.chunks" tunnel="yes" select="false()"/>
  <xsl:choose>
    <xsl:when test="$osgi.collecting.chunks">
      <!-- Re-entered for a namespace converted copy of the document -->
      <xsl:next-match/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:variable name="chunks">
        <xsl:next-match>
          <xsl:with-param name="osgi.collecting.chunks" tunnel="yes" select="true()"/>
        </xsl:next-match>
      </xsl:variable>
      <xsl:for-each select="$chunks//osgi:chunk">
        <xsl:call-template name="osgi.write.chunk"/>
      </xsl:for-each>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="osgi.write.chunk">
  <!-- A zero length doctype is not allowed, so it needs its own branch -->
  <xsl:choose>
    <xsl:when test="@doctype-public != '' and @doctype-system != ''">
      <xsl:result-document href="{@href}" method="{@method}" encoding="{@encoding}"
                           indent="{@indent}" omit-xml-declaration="{@omit-xml-declaration}"
                           doctype-public="{@doctype-public}" doctype-system="{@doctype-system}">
        <xsl:apply-templates select="node()" mode="osgi.chunk.content"/>
      </xsl:result-document>
    </xsl:when>
    <xsl:when test="@doctype-system != ''">
      <xsl:result-document href="{@href}" method="{@method}" encoding="{@encoding}"
                           indent="{@indent}" omit-xml-declaration="{@omit-xml-declaration}"
                           doctype-system="{@doctype-system}">
        <xsl:apply-templates select="node()" mode="osgi.chunk.content"/>
      </xsl:result-document>
    </xsl:when>
    <xsl:otherwise>
      <xsl:result-document href="{@href}" method="{@method}" encoding="{@encoding}"
                           indent="{@indent}" omit-xml-declaration="{@omit-xml-declaration}">
        <xsl:apply-templates select="node()" mode="osgi.chunk.content"/>
      </xsl:result-document>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!-- Nested chunks are written to their own files -->
<xsl:template match="osgi:chunk" mode="osgi.chunk.content"/>

<xsl:template match="*" mode="osgi.chunk.content">
  <!-- Drops the marker namespace, which is in scope for all the content -->
  <xsl:copy copy-namespaces="no">
    <xsl:copy-of select="@*"/>
    <xsl:apply-templates select="node()" mode="osgi.chunk.content"/>
  </xsl:copy>
</xsl:template>

<xsl:template match="text()|comment()|processing-instruction()" mode="osgi.chunk.content">
  <xsl:copy/>
</xsl:template>

<xsl:template name="make-relative-filename">
  <xsl:param name="base.dir" select="'./'"/>
  <xsl:param name="base.name" select="''"/>
  <!-- result-document resolves against the base output URI, not the chunk -->
  <xsl:value-of select="concat($base.dir, $base.name)"/>
</xsl:template>

<xsl:template name="write.chunk">
  <xsl:param name="filename" select="''"/>
  <xsl:param name="quiet" select="$chunk.quietly"/>
  <xsl:param name="suppress-context-node-name" select="0"/>
  <xsl:param name="message-prolog"/>
  <xsl:param name="message-epilog"/>

  <xsl:param name="method" select="$chunker.output.method"/>
  <xsl:param name="encoding" select="$chunker.output.encoding"/>
  <xsl:param name="indent" select="$chunker.output.indent"/>
  <xsl:param name="omit-xml-declaration" select="$chunker.output.omit-xml-declaration"/>
  <xsl:param name="standalone" select="$chunker.output.standalone"/>
  <xsl:param name="doctype-public" select="$chunker.output.doctype-public"/>
  <xsl:param name="doctype-system" select="$chunker.output.doctype-system"/>
  <xsl:param name="media-type" select="$chunker.output.media-type"/>
  <xsl:param name="cdata-section-elements" select="$chunker.output.cdata-section-elements"/>

  <xsl:param name="content"/>

  <xsl:if test="string($quiet) = '0'">
    <xsl:message>
      <xsl:value-of select="$message-prolog"/>
      <xsl:text>Writing </xsl:text>
      <xsl:value-of select="$filename"/>
      <xsl:if test="name(.) != '' and string($suppress-context-node-name) = '0'">
        <xsl:text> for </xsl:text>
        <xsl:value-of select="name(.)"/>
        <xsl:if test="@id or @xml:id">
          <xsl:text>(</xsl:text>
          <xsl:value-of select="(@id|@xml:id)[1]"/>
          <xsl:text>)</xsl:text>
        </xsl:if>
      </xsl:if>
      <xsl:value-of select="$message-epilog"/>
    </xsl:message>
  </xsl:if>

  <osgi:chunk href="{$filename}" method="{$method}" encoding="{$encoding}"
              indent="{$indent}" omit-xml-declaration="{$omit-xml-declaration}"
              doctype-public="{$doctype-public}" doctype-system="{$doctype-system}">
    <xsl:copy-of select="$content"/>
  </osgi:chunk>
</xsl:template>

<xsl:template name="write.chunk.with.doctype">
  <xsl:param name="filename" select="''"/>
  <xsl:param name="quiet" select="$chunk.quietly"/>

  <xsl:param name="method" select="$chunker.output.method"/>
  <xsl:param name="encoding" select="$chunker.output.encoding"/>
  <xsl:param name="indent" select="$chunker.output.indent"/>
  <xsl:param name="omit-xml-declaration" select="$chunker.output.omit-xml-declaration"/>
  <xsl:param name="standalone" select="$chunker.output.standalone"/>
  <xsl:param name="doctype-public" select="$chunker.output.doctype-public"/>
  <xsl:param name="doctype-system" select="$chunker.output.doctype-system"/>
  <xsl:param name="media-type" select="$chunker.output.media-type"/>
  <xsl:param name="cdata-section-elements" select="$chunker.output.cdata-section-elements"/>

  <xsl:param name="content"/>

  <xsl:call-template name="write.chunk">
    <xsl:with-param name="filename" select="$filename"/>
    <xsl:with-param name="quiet" select="$quiet"/>
    <xsl:with-param name="method" select="$method"/>
    <xsl:with-param name="encoding" select="$encoding"/>
    <xsl:with-param name="indent" select="$indent"/>
    <xsl:with-param name="omit-xml-declaration" select="$omit-xml-declaration"/>
    <xsl:with-param name="doctype-public" select="$doctype-public"/>
    <xsl:with-param name="doctype-system" select="$doctype-system"/>
    <xsl:with-param name="content" select="$content"/>
  </xsl:call-template>
</xsl:template>

<xsl:template name="write.text.chunk">
  <xsl:param name="filename" select="''"/>
  <xsl:param name="quiet" select="$chunk.quietly"/>
  <xsl:param name="suppress-context-node-name" select="0"/>
  <xsl:param name="message-prolog"/>
  <xsl:param name="message-epilog"/>
  <xsl:param name="method" select="'text'"/>
  <xsl:param name="encoding" select="$chunker.output.encoding"/>
  <xsl:param name="media-type" select="$chunker.output.media-type"/>
  <xsl:param name="content"/>

  <xsl:call-template name="write.chunk">
    <xsl:with-param name="filename" select="$filename"/>
    <xsl:with-param name="quiet" select="$quiet"/>
    <xsl:with-param name="suppress-context-node-name" select="$suppress-context-node-name"/>
    <xsl:with-param name="message-prolog" select="$message-prolog"/>
    <xsl:with-param name="message-epilog" select="$message-epilog"/>
    <xsl:with-param name="method" select="$method"/>
    <xsl:with-param name="encoding" select="$encoding"/>
    <xsl:with-param name="indent" select="'no'"/>
    <xsl:with-param name="omit-xml-declaration" select="'yes'"/>
    <xsl:with-param name="doctype-public" select="''"/>
    <xsl:with-param name="doctype-system" select="''"/>
    <xsl:with-param name="content" select="$content"/>
  </xsl:call-template>
</xsl:template>

</xsl:stylesheet>
