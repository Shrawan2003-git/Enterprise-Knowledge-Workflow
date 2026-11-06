export interface DocumentSummaryDto {
  id: string;
  title: string;
  description?: string;
  status: string;
  classification: string;
  categoryName?: string;
  tags: string[];
  ownerId: string;
  departmentId?: string;
  currentVersionNumber?: number;
  expiryAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface VersionDto {
  id: string;
  documentId: string;
  versionNumber: number;
  checksum: string;
  mimeType: string;
  sizeBytes: number;
  uploadedBy: string;
  changeNote?: string;
  processingStatus: string;
  createdAt: string;
}

export interface DocumentPermissionDto {
  principalType: string;
  principalId: string;
  permissionLevel: string;
}

export interface DocumentDetailDto {
  id: string;
  title: string;
  description?: string;
  status: string;
  classification: string;
  categoryName?: string;
  tags: string[];
  ownerId: string;
  departmentId?: string;
  currentVersion?: VersionDto;
  expiryAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  permissions: DocumentPermissionDto[];
}

export interface Category {
  id: string;
  name: string;
  description?: string;
}

export interface Tag {
  id: string;
  name: string;
}

export interface Page<T> {
  content: T[];
  pageable: any;
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
