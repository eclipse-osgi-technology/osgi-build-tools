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
  Lists the absolute URI of every image in the book, one per line, so that
  they can be copied next to the HTML pages. XInclude sets xml:base on each
  included file, so base-uri() gives the directory the image is relative to.
-->
<xsl:stylesheet
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:d="http://docbook.org/ns/docbook"
  version="3.0">

<xsl:output method="text" encoding="UTF-8"/>

<xsl:template match="/">
  <xsl:for-each select="distinct-values(//d:imagedata[@fileref]/resolve-uri(@fileref, base-uri(.)))">
    <xsl:value-of select="."/>
    <xsl:text>&#xa;</xsl:text>
  </xsl:for-each>
</xsl:template>

</xsl:stylesheet>
