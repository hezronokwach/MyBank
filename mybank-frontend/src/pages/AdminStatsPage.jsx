import Layout from '../components/Layout';
import AssistantMetricsPanel from '../components/admin/AssistantMetricsPanel';

// /admin/stats: how the AI assistant is being used. The backend only lets admins load these numbers.
const AdminStatsPage = () => {
    return (
        <Layout>
            <AssistantMetricsPanel />
        </Layout>
    );
};

export default AdminStatsPage;
