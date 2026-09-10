import LoginForm from '../components/LoginForm';
import { Link } from 'react-router-dom';

const LoginPage = () => {
    return (
        <div className="auth-page">
            <Link to="/" className="brand">MyBank</Link>
            <LoginForm />
        </div>
    );
};

export default LoginPage;
