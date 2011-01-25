<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="/vap/Application/property[@name='panels' and @class='Panel']">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:attribute name="name">plotElements</xsl:attribute>
			<xsl:attribute name="class">PlotElement</xsl:attribute>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="/vap/Application/property/Panel">
		<xsl:element name="PlotElement">
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="/vap/Application/property/Panel/property/PanelStyle">
		<xsl:element name="PlotElementStyle">
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates/>
		</xsl:element>
	</xsl:template>

	<xsl:template match="*">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>