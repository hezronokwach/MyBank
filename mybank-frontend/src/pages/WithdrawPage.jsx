import { useLocation } from 'react-router-dom';
import WithdrawForm from '../components/WithdrawForm';
import Layout from '../components/Layout';
import { Link } from 'react-router-dom';

const WithdrawPage = () => {
    const location = useLocation();
    const { accountNumber } = location.state || {};
    
    return (
        <Layout><div className="form-page"><Link to="/dashboard" className="back-link">← Back to dashboard</Link><WithdrawForm accountNumber={accountNumber} /></div></Layout>
    );
};

export default WithdrawPage;
