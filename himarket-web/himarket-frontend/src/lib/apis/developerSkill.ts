import request, { type RespI } from "../request";

export type SkillListType = "all" | "personal" | "official";

export interface DeveloperSkillResult {
  productId: string;
  name: string;
  description?: string;
  tags: string[];
  isOwner: boolean;
  isOfficial: boolean;
  status: string;
  createdAt?: number;
  developerUsername?: string;
}

interface CreateSkillReq {
  name: string;
  description?: string;
  tags?: string[];
}

interface UpdateSkillReq {
  name?: string;
  description?: string;
  tags?: string[];
}

export function listDeveloperSkills(params: {
  type?: SkillListType;
  tag?: string;
}) {
  return request.get<RespI<DeveloperSkillResult[]>, RespI<DeveloperSkillResult[]>>(
    "/developer/skills",
    { params }
  );
}

export function createDeveloperSkill(data: CreateSkillReq) {
  return request.post<RespI<DeveloperSkillResult>, RespI<DeveloperSkillResult>>(
    "/developer/skills",
    data
  );
}

export function getDeveloperSkill(productId: string) {
  return request.get<RespI<DeveloperSkillResult>, RespI<DeveloperSkillResult>>(
    `/developer/skills/${productId}`
  );
}

export function updateDeveloperSkill(productId: string, data: UpdateSkillReq) {
  return request.put<RespI<DeveloperSkillResult>, RespI<DeveloperSkillResult>>(
    `/developer/skills/${productId}`,
    data
  );
}

export function deleteDeveloperSkill(productId: string) {
  return request.delete<RespI<null>, RespI<null>>(
    `/developer/skills/${productId}`
  );
}

export function uploadDeveloperSkillPackage(productId: string, file: File) {
  const form = new FormData();
  form.append("file", file);
  return request.post<RespI<null>, RespI<null>>(
    `/developer/skills/${productId}/package`,
    form,
    { headers: { "Content-Type": "multipart/form-data" } }
  );
}
