package io.ybigta.text2sql.infer.core

import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.OutputKind
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.structure.SafeParentInfo

/**
 * kotlinx.serialization에서 xml 직렬화 할때 jackson과 비슷한 포멧으로 출력하기 위함
 * reference: https://github.com/pdvrieze/xmlutil/blob/master/examples/JACKSON.md
 */
object JacksonPolicy : DefaultXmlSerializationPolicy(
    {
        pedantic = false
        encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER
    }
) {
    /*
     * Rather than replacing the method wholesale, just make attributes into elements unless the [XmlElement] annotation
     * is present with a `false` value on the value attribute.
     */
    override fun effectiveOutputKind(
        serializerParent: SafeParentInfo,
        tagParent: SafeParentInfo,
        canBeAttribute: Boolean
    ): OutputKind {
        val r = super.effectiveOutputKind(serializerParent, tagParent, canBeAttribute)
        return when {
            // Do take into account the XmlElement annotation
            r == OutputKind.Attribute &&
                    serializerParent.useAnnIsElement != false ->
                OutputKind.Element

            else -> r
        }
    }

    /**
     * Jackson naming policy is based upon use name only. However, for this policy we do take the type annotation
     * if it is available. If there is no annotation for the name, we get the name out of the useName in all cases
     * (the default policy is dependent on member kind and the output used (attribute vs element)).
     */
    override fun effectiveName(
        serializerParent: SafeParentInfo,
        tagParent: SafeParentInfo,
        outputKind: OutputKind,
        useName: XmlSerializationPolicy.DeclaredNameInfo
    ): QName {
        return useName.annotatedName
            ?: serializerParent.elementTypeDescriptor.typeQname
            ?: serialUseNameToQName(useName, tagParent.namespace)
    }

}