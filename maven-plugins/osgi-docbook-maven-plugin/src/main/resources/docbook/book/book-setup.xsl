<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:db="http://docbook.org/ns/docbook"
    xmlns="http://docbook.org/ns/docbook"
    exclude-result-prefixes="db xs">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <xsl:param name="title" select="'No title provided'" />
    <xsl:param name="version" select="'No version provided'" />
    <xsl:param name="publishDate" select="string(current-date())" />

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="db:info/db:title">
        <title>
            <xsl:value-of select="$title"/>
        </title>
    </xsl:template>

    <xsl:template match="db:info/db:releaseinfo">
        <title>
            <xsl:value-of select="concat('Release ', $version)"/>
        </title>
    </xsl:template>

    <xsl:template match="db:info/db:pubdate">
        <pubdate>
            <xsl:value-of select="format-date(xs:date($publishDate), '[MNn] [Y]')"/>
        </pubdate>
    </xsl:template>

    <xsl:template match="db:info/db:copyright/db:year">
        <year>
            <xsl:text>2000, </xsl:text>
            <xsl:value-of select="format-date(xs:date($publishDate), '[Y]')"/>
        </year>
    </xsl:template>

</xsl:stylesheet>