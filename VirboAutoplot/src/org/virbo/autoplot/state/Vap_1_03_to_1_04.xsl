<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

        <xsl:template match="//Plot/property[@name='zaxis']/Axis">
		<xsl:variable name="plotId" select="../../@id"/>
		<xsl:copy>
			<xsl:copy-of select="@*"/>
                        <xsl:element name="property">
                            <xsl:attribute name='name'>visible</xsl:attribute>
                            <xsl:attribute name='type'>Boolean</xsl:attribute>
                            <xsl:attribute name='value'>false</xsl:attribute>
                            <xsl:for-each select="//PlotElement[property[@name='plotId' and @value=$plotId]]">
    				<!--<xsl:if test="property[@name='renderType' and @value='spectrogram'] or property[@name='renderType' and @value='nnSpectrogram'] or property[@name='renderType' and @value='colorScatter'] " >-->
                                <xsl:if test="property[@name='renderType' and (@value='spectrogram' or @value='nnSpectrogram' or @value='colorScatter')] " >
					<xsl:attribute name='value'>true</xsl:attribute> <!-- set the one that is visible-->
				</xsl:if>
                            </xsl:for-each>
                        </xsl:element>
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