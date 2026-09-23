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
  These templates link them to the published specifications instead, using an
  index of the chapters, sections and packages of each published book (see
  docbook/links/osgi-spec-index.xml and src/tools/link-index.xsl).
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

<!-- The URI of the index, empty to leave references without a target as text -->
<xsl:param name="external.links.index" select="''"/>
<!-- The URL that the book paths in the index are relative to -->
<xsl:param name="external.links.base" select="'https://docs.osgi.org/specification/'"/>

<xsl:variable name="osgi.external.index" as="document-node()?"
              select="if ($external.links.index != '') then doc($external.links.index) else ()"/>

<xsl:key name="osgi.external.target" match="l:target" use="@id"/>

<!--
  Finds the published target of a linkend: an indexed chapter or section, the
  package holding a javadoc id (such as org.osgi.util.pushstream.PushStream or
  org.osgi.service.event-version), or else the chapter that the id is scoped
  to (ids of the form chapter-local).
-->
<xsl:function name="osgi:external-target" as="element()?">
  <xsl:param name="linkend" as="xs:string?"/>
  <xsl:if test="exists($osgi.external.index) and $linkend">
    <xsl:variable name="exact" select="key('osgi.external.target', $linkend, $osgi.external.index)[1]"/>
    <xsl:variable name="package" as="element()?">
      <xsl:if test="empty($exact) and starts-with($linkend, 'org.osgi.')">
        <!-- The longest match, so org.osgi.service.cm.annotations wins over org.osgi.service.cm -->
        <xsl:for-each select="$osgi.external.index//l:package
                                [$linkend = @name or starts-with($linkend, concat(@name, '.'))
                                 or starts-with($linkend, concat(@name, '-'))]">
          <xsl:sort select="string-length(@name)" data-type="number" order="descending"/>
          <xsl:if test="position() = 1">
            <xsl:sequence select="."/>
          </xsl:if>
        </xsl:for-each>
      </xsl:if>
    </xsl:variable>
    <xsl:sequence select="($exact, $package,
        key('osgi.external.target', substring-before($linkend, '-'), $osgi.external.index)[1])[1]"/>
  </xsl:if>
</xsl:function>

<!-- True for the chapter or appendix that a published page is made from -->
<xsl:function name="osgi:is-chunk" as="xs:boolean">
  <xsl:param name="target" as="element()"/>
  <xsl:sequence select="$target/self::l:target and concat($target/@id, '.html') = $target/@file"/>
</xsl:function>

<xsl:function name="osgi:external-href" as="xs:string">
  <xsl:param name="target" as="element()"/>
  <xsl:param name="linkend" as="xs:string"/>
  <!-- As DocBook does within a book, a chunk is linked without a fragment -->
  <xsl:sequence select="concat($external.links.base, $target/ancestor::l:book/@path, $target/@file,
      if (osgi:is-chunk($target)) then '' else concat('#', $linkend))"/>
</xsl:function>

<!--
  The content of an xref to a published target. Javadoc ids are only indexed
  by package, so their text is the member name (PushStream, Promise.then()).
-->
<xsl:function name="osgi:external-text" as="item()*">
  <xsl:param name="target" as="element()"/>
  <xsl:param name="linkend" as="xs:string"/>
  <xsl:choose>
    <xsl:when test="$target/self::l:package">
      <xsl:variable name="member" select="substring-after($linkend, concat($target/@name, '.'))"/>
      <xsl:sequence select="if ($member = '') then string($target/@name)
                            else if (contains($member, '-')) then concat(substring-before($member, '-'), '()')
                            else $member"/>
    </xsl:when>
    <xsl:when test="osgi:is-chunk($target)">
      <!-- DocBook emphasises the titles of chapters in cross references -->
      <em><xsl:value-of select="$target/@title"/></em>
    </xsl:when>
    <xsl:otherwise>
      <xsl:sequence select="string($target/@title)"/>
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

<!-- Without this DocBook renders an xref to an id outside the book as ??? -->
<xsl:template match="d:xref[@linkend][empty(key('id', @linkend))][exists(osgi:external-target(@linkend))]">
  <xsl:variable name="target" select="osgi:external-target(@linkend)"/>
  <xsl:call-template name="osgi.external.link">
    <xsl:with-param name="content" select="osgi:external-text($target, @linkend)"/>
  </xsl:call-template>
</xsl:template>

</xsl:stylesheet>
