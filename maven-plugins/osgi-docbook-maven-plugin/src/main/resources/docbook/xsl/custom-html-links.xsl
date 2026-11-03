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
  A specification repository only holds one chapter, so references to other
  chapters (and to the javadoc of their packages) have no target in the book.
  These templates link them to the published specifications instead, using the
  index lookup of custom-links.xsl.
-->
<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:d="http://docbook.org/ns/docbook"
  xmlns:l="urn:org.osgi:docbook:links"
  xmlns:osgi="urn:org.osgi:docbook"
  xmlns="http://www.w3.org/1999/xhtml"
  exclude-result-prefixes="xs d l osgi"
  version="3.0">

<xsl:import href="custom-links.xsl"/>

<!--
  The content of an xref to a published target, with the markup of the HTML
  book: DocBook emphasises the titles of chapters in cross references.
-->
<xsl:function name="osgi:external-text" as="item()*">
  <xsl:param name="target" as="element()"/>
  <xsl:param name="linkend" as="xs:string"/>
  <xsl:choose>
    <xsl:when test="osgi:is-chunk($target)">
      <em><xsl:value-of select="osgi:external-label($target, $linkend)"/></em>
    </xsl:when>
    <xsl:otherwise>
      <xsl:sequence select="osgi:external-label($target, $linkend)"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!-- Called for a link whose linkend is not in the book -->
<xsl:template name="osgi.external.link">
  <xsl:param name="linkend" select="@linkend"/>
  <xsl:param name="content"/>
  <xsl:variable name="target" select="osgi:external-target($linkend)"/>
  <xsl:choose>
    <xsl:when test="exists($target)">
      <a class="{local-name()} external" href="{osgi:external-href($target, $linkend)}">
        <xsl:if test="$target/@title">
          <xsl:attribute name="title" select="string-join(($target/@label, $target/@title), '&#160;')"/>
        </xsl:if>
        <xsl:copy-of select="$content"/>
      </a>
    </xsl:when>
    <xsl:otherwise>
      <xsl:copy-of select="$content"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!--
  Without this DocBook renders an xref to an id outside the book as ???. An
  endterm that names the version of a package, as in the release info of a
  chapter, gives the version that the published chapter shows.
-->
<xsl:template match="d:xref[@linkend][empty(key('id', @linkend))][exists(osgi:external-target(@linkend))]">
  <xsl:variable name="target" select="osgi:external-target(@linkend)"/>
  <xsl:variable name="version" select="osgi:external-version(@endterm)"/>
  <xsl:call-template name="osgi.external.link">
    <xsl:with-param name="content"
                    select="if (exists($version)) then concat('Version ', $version)
                            else osgi:external-text($target, @linkend)"/>
  </xsl:call-template>
</xsl:template>

</xsl:stylesheet>
