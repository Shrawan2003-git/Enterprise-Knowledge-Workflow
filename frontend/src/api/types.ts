export interface UserDto {
  id: string;
  email: string;
  displayName: string;
  status: string;
  departmentId?: string;
  roles: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserDto;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
  correlationId: string;
  timestamp: string;
}
