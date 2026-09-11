import { useLocation } from 'react-router-dom';
import TransferOwnershipForm from '../components/TransferOwnershipForm';
import Layout from '../components/Layout';
import { Link } from 'react-router-dom';

const TransferOwnershipPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <Layout><div className="form-page"><Link to="/dashboard" className="back-link">← Back to dashboard</Link><TransferOwnershipForm accountNumber={accountNumber} /></div></Layout>
    );
};

export default TransferOwnershipPage;
