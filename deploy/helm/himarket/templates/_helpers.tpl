{{- define "himarket.serviceAccountName" -}}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}

{{/*
Map a size name (small/standard/large) to resource requests/limits.
Usage: {{ include "himarket.sizeToResources" "standard" }}
*/}}
{{- define "himarket.sizeToResources" -}}
{{- if eq (. | toString) "small" }}
requests:
  cpu: "1"
  memory: "2Gi"
limits:
  cpu: "1"
  memory: "2Gi"
{{- else if eq (. | toString) "large" }}
requests:
  cpu: "4"
  memory: "8Gi"
limits:
  cpu: "4"
  memory: "8Gi"
{{- else }}
requests:
  cpu: "2"
  memory: "4Gi"
limits:
  cpu: "2"
  memory: "4Gi"
{{- end }}
{{- end }}

{{/*
Map a size name to replica count. Only "large" gets 2 replicas.
Usage: {{ include "himarket.sizeToReplicas" "standard" }}
*/}}
{{- define "himarket.sizeToReplicas" -}}
{{- if eq (. | toString) "large" }}2{{- else }}1{{- end }}
{{- end }}

{{/*
Lightweight resource profile for Nginx-based frontend containers.
Usage: {{ include "himarket.sizeToResourcesLight" "standard" }}
*/}}
{{- define "himarket.sizeToResourcesLight" -}}
{{- if eq (. | toString) "small" }}
requests:
  cpu: "0.5"
  memory: "512Mi"
limits:
  cpu: "0.5"
  memory: "512Mi"
{{- else if eq (. | toString) "large" }}
requests:
  cpu: "2"
  memory: "2Gi"
limits:
  cpu: "2"
  memory: "2Gi"
{{- else }}
requests:
  cpu: "1"
  memory: "1Gi"
limits:
  cpu: "1"
  memory: "1Gi"
{{- end }}
{{- end }}
