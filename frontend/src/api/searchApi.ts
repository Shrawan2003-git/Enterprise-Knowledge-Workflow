export interface SearchResultDto {
  id: string;
  title: string;
  description?: string;
  categoryName?: string;
  classification: string;
  status: string;
  snippet?: string;
  score?: number;
}

export interface SearchParams {
  q?: string;
  category?: string;
  tag?: string;
  department?: string;
  status?: string;
  owner?: string;
  classification?: string;
  page?: number;
  size?: number;
}

export interface SearchPageResult {
  content: SearchResultDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const searchApi = {
  async search(params: SearchParams): Promise<SearchPageResult> {
    const query = new URLSearchParams();
    if (params.q) query.set('q', params.q);
    if (params.category) query.set('category', params.category);
    if (params.tag) query.set('tag', params.tag);
    if (params.department) query.set('department', params.department);
    if (params.status) query.set('status', params.status);
    if (params.owner) query.set('owner', params.owner);
    if (params.classification) query.set('classification', params.classification);
    if (params.page !== undefined) query.set('page', params.page.toString());
    if (params.size !== undefined) query.set('size', params.size.toString());

    const res = await fetch(`/api/v1/search?${query.toString()}`, {
      headers: getAuthHeaders(),
    });

    if (!res.ok) {
      throw new Error('Failed to execute search');
    }
    return res.json();
  }
};
