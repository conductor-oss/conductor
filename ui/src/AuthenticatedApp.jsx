import React, { useState, useEffect } from 'react';
import { googleLogout, useGoogleLogin } from '@react-oauth/google';
import Login from './components/Login';
import axios from 'axios';
import App from './App';

function AuthenticatedApp() {
    const profileFromStorage = localStorage.getItem('conductorUserProfile');
    const [ profile, setProfile ] = useState(JSON.parse(profileFromStorage));
    const successfulLogin = (profile) => {
        setProfile(profile);
        localStorage.setItem('conductorUserProfile', JSON.stringify(profile));
    };
    // log out function to log the user out of google and set the profile array to null
    const logOut = () => {
        googleLogout();
        setProfile(null);
        localStorage.removeItem('conductorUserProfile');
    };
    return (
        <div>
            {profile ? (
                <App profile={profile} logOut={logOut}/>
            ) : (
                <Login onSuccess={successfulLogin} />
            )}
        </div>
    );
}
export default AuthenticatedApp;