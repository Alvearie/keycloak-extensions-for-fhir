/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.alvearie.keycloak;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

/**
 * Adds a "Patient/" prefix to a user attribute and maps it to a token claim.
 */
public class PatientPrefixUserAttributeMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final String PATIENT_PREFIX = "Patient/";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(ProtocolMapperUtils.USER_ATTRIBUTE);
        property.setLabel(ProtocolMapperUtils.USER_MODEL_ATTRIBUTE_LABEL);
        property.setHelpText(ProtocolMapperUtils.USER_MODEL_ATTRIBUTE_HELP_TEXT);
        property.setType(ProviderConfigProperty.STRING_TYPE);
        configProperties.add(property);

        OIDCAttributeMapperHelper.addAttributeConfig(configProperties, PatientPrefixUserAttributeMapper.class);

        property = new ProviderConfigProperty();
        property.setName(ProtocolMapperUtils.MULTIVALUED);
        property.setLabel(ProtocolMapperUtils.MULTIVALUED_LABEL);
        property.setHelpText(ProtocolMapperUtils.MULTIVALUED_HELP_TEXT);
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName(ProtocolMapperUtils.AGGREGATE_ATTRS);
        property.setLabel(ProtocolMapperUtils.AGGREGATE_ATTRS_LABEL);
        property.setHelpText(ProtocolMapperUtils.AGGREGATE_ATTRS_HELP_TEXT);
        property.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        configProperties.add(property);
    }

    public static final String PROVIDER_ID = "oidc-patient-prefix-usermodel-attribute-mapper";

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Patient Prefix User Attribute";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "Adds a '" + PATIENT_PREFIX + "' prefix to a user attribute and maps it to a token claim.";
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel, UserSessionModel userSession) {
        UserModel user = userSession.getUser();
        String attributeName = mappingModel.getConfig().get(ProtocolMapperUtils.USER_ATTRIBUTE);
        boolean aggregateAttrs = Boolean.valueOf(mappingModel.getConfig().get(ProtocolMapperUtils.AGGREGATE_ATTRS));
        Collection<String> attributeValue = KeycloakModelUtils.resolveAttribute(user, attributeName, aggregateAttrs);
        if (attributeValue == null)
            return;
        attributeValue = attributeValue.stream().map(k -> PATIENT_PREFIX.concat(k)).collect(Collectors.toList());
        OIDCAttributeMapperHelper.mapClaim(token, mappingModel, attributeValue);
    }

    public static ProtocolMapperModel createClaimMapper(String name,
        String userAttribute,
        String tokenClaimName, String claimType,
        boolean accessToken, boolean idToken, boolean multivalued) {
        return createClaimMapper(name, userAttribute, tokenClaimName, claimType, accessToken, idToken, multivalued, false);
    }

    public static ProtocolMapperModel createClaimMapper(String name,
        String userAttribute,
        String tokenClaimName, String claimType,
        boolean accessToken, boolean idToken,
        boolean multivalued, boolean aggregateAttrs) {
        ProtocolMapperModel mapper =
                OIDCAttributeMapperHelper.createClaimMapper(name, userAttribute, tokenClaimName, claimType, accessToken, idToken, PROVIDER_ID);

        if (multivalued) {
            mapper.getConfig().put(ProtocolMapperUtils.MULTIVALUED, "true");
        }
        if (aggregateAttrs) {
            mapper.getConfig().put(ProtocolMapperUtils.AGGREGATE_ATTRS, "true");
        }

        return mapper;
    }
}
