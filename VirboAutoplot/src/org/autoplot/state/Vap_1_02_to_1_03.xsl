<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="PlotElement/property[@name='parentPanel']"> 
		<xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="name">parent</xsl:attribute>
		    <xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="*">
		<xsl:copy>
			<xsl:copy-of select="@*"/>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

</xsl:stylesheet>