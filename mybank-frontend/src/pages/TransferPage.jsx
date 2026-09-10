import TransferForm from '../components/TransferForm';
import Layout from '../components/Layout';
import { Link } from 'react-router-dom';

const TransferPage = () => {
    return (
        <Layout><div className="form-page"><Link to="/dashboard" className="back-link">← Back to dashboard</Link><TransferForm /></div></Layout>
    );
};

export default TransferPage;
