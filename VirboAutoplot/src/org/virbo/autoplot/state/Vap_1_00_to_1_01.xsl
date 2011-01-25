<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:template match="Axis/property[@name='autolabel']"> <!-- need to update autorange and autolabel -->
		<xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="name">autoLabel</xsl:attribute>
		    <xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>

        <xsl:template match="Axis/property[@name='autorange']"> <!-- need to update autorange and autolabel -->
		<xsl:copy>
                    <xsl:copy-of select="@*"/>
                    <xsl:attribute name="name">autoRange</xsl:attribute>
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