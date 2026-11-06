export interface OverviewReportDto {
  documents: {
    totalCount: number;
  };
  tasks: {
    openCount: number;
    inProgressCount: number;
    overdueCount: number;
  };
  ingestion: {
    failuresCount: number;
    status: string;
  };
}

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('knowflow_access_token');
  return {
    'Authorization': token ? `Bearer ${token}` : '',
  };
}

export const reportingApi = {
  async getOverview(): Promise<OverviewReportDto> {
    const res = await fetch('/api/v1/reports/overview', {
      headers: getAuthHeaders(),
    });
    if (!res.ok) throw new Error('Failed to load overview report');
    return res.json();
  }
};
