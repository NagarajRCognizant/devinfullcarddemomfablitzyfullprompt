{{/* Common naming and labels. */}}
{{- define "carddemo.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "carddemo.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "carddemo.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "carddemo.labels" -}}
app.kubernetes.io/name: {{ include "carddemo.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/* Fully qualified image reference for one component. */}}
{{- define "carddemo.image" -}}
{{- $registry := .root.Values.image.registry -}}
{{- if $registry -}}
{{ printf "%s/%s:%s" $registry .component.repository (.root.Values.image.tag | toString) }}
{{- else -}}
{{ printf "%s:%s" .component.repository (.root.Values.image.tag | toString) }}
{{- end -}}
{{- end -}}

{{/*
The realm every service validates against. No key is templated: the services discover the realm's
JWKS and verify the signature with it, so the chart carries no signing material at all.
*/}}
{{- define "carddemo.realmEnv" -}}
- name: KEYCLOAK_URL
  value: {{ .Values.keycloak.url | quote }}
- name: KEYCLOAK_REALM
  value: {{ .Values.keycloak.realm | quote }}
- name: KEYCLOAK_ISSUER_URI
  value: {{ .Values.keycloak.issuerUri | quote }}
- name: CARDDEMO_TOKEN_CLOCK_SKEW_SECONDS
  value: {{ .Values.keycloak.clockSkewSeconds | quote }}
- name: CARDDEMO_TOKEN_REQUIRE_SECOND_FACTOR
  value: {{ .Values.keycloak.requireSecondFactor | quote }}
{{- end -}}

{{/*
The confidential client of one service, when it has one: the identity service to provision realm
users, the authorization service to get its own token for the Kafka-driven flow. The secret is only
ever referenced from a secret managed outside this chart.
*/}}
{{- define "carddemo.clientEnv" -}}
{{- with .component.keycloakClient }}
- name: KEYCLOAK_CLIENT_ID
  value: {{ .clientId | quote }}
- name: KEYCLOAK_CLIENT_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ required "a Kubernetes secret holding the client secret must be named" .existingSecret }}
      key: {{ .secretKey }}
{{- end }}
{{- end -}}

{{/* Datasource and realm environment shared by the services. */}}
{{- define "carddemo.serviceEnv" -}}
- name: SERVER_ADDRESS
  value: "0.0.0.0"
- name: {{ .component.datasource.urlVariable }}
  value: {{ .component.datasource.url | quote }}
- name: {{ .component.datasource.userVariable }}
  valueFrom:
    secretKeyRef:
      name: {{ .component.datasource.existingSecret }}
      key: {{ .component.datasource.usernameKey }}
- name: {{ .component.datasource.passwordVariable }}
  valueFrom:
    secretKeyRef:
      name: {{ .component.datasource.existingSecret }}
      key: {{ .component.datasource.passwordKey }}
{{ include "carddemo.realmEnv" .root }}
{{- include "carddemo.clientEnv" . }}
{{- end -}}

{{/* Liveness and readiness from the Spring Boot actuator health groups. */}}
{{- define "carddemo.probes" -}}
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  initialDelaySeconds: 20
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  initialDelaySeconds: 10
  periodSeconds: 5
{{- end -}}
