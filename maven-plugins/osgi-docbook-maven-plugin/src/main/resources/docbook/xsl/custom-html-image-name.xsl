<?xml version="1.0" encoding="utf-8"?>
<!--
    Copyright (c) Contributors to the Eclipse Foundation

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
-->
<!-- The file name of an image in the images/ directory of the HTML pages,
    shared by custom-html.xsl (the references) and custom-html-images.xsl
    (the list of images OsgiSpecHtmlMojo copies or rasterizes).

    By default the file name of the image. For a complete book, SVG images
    get the label of their chapter as a prefix, as the published
    specifications do (002-delegation-model.svg), because chapters may use
    the same file name; with image.rasterize they become PNG images. -->
<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:d="http://docbook.org/ns/docbook"
    version="2.0">

<xsl:param name="image.chapter.prefix" select="0"/>
<xsl:param name="image.rasterize" select="0"/>

<xsl:template name="osgi.image.filename">
    <!-- the fileref attribute -->
    <xsl:param name="fileref" select="."/>
    <xsl:variable name="name" select="tokenize(string($fileref), '/')[last()]"/>
    <xsl:choose>
        <xsl:when test="ends-with(lower-case($name), '.svg') and
                        (number($image.chapter.prefix) = 1 or number($image.rasterize) = 1)">
            <xsl:if test="number($image.chapter.prefix) = 1">
                <xsl:variable name="label" select="string($fileref/ancestor::d:chapter[1]/@label)"/>
                <xsl:choose>
                    <xsl:when test="string(number($label)) != 'NaN'">
                        <xsl:value-of select="format-number(number($label), '000')"/>
                    </xsl:when>
                    <xsl:otherwise>0</xsl:otherwise>
                </xsl:choose>
                <xsl:text>-</xsl:text>
            </xsl:if>
            <xsl:choose>
                <xsl:when test="number($image.rasterize) = 1">
                    <xsl:value-of select="concat(substring($name, 1, string-length($name) - 4), '.png')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$name"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:when>
        <xsl:otherwise>
            <xsl:value-of select="$name"/>
        </xsl:otherwise>
    </xsl:choose>
</xsl:template>

</xsl:stylesheet>
