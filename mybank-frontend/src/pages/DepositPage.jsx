import { useLocation } from 'react-router-dom';
import DepositForm from '../components/DepositForm';
import Layout from '../components/Layout';
import { Link } from 'react-router-dom';

const DepositPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <Layout><div className="form-page"><Link to="/dashboard" className="back-link">← Back to dashboard</Link><DepositForm accountNumber={accountNumber} /></div></Layout>
    );
};

export default DepositPage;
