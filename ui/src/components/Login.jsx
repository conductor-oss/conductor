import React, { useState, useEffect } from 'react';
import { Button, Box, Typography, Container, Paper } from '@mui/material';
import GoogleIcon from '@mui/icons-material/Google';
import { useGoogleLogin } from '@react-oauth/google';
import axios from 'axios';
const LoginComponent = (props) => {

    const [ user, setUser ] = useState(null);
    const login = useGoogleLogin({
        onSuccess: (codeResponse) => setUser(codeResponse),
        onError: (error) => console.log('Login Failed:', error)
    });

    useEffect(
        () => {
            if (user && user.hd != 'freshworks.com') {
              alert('Only Freshworks Email allowed currently!');
              return;
            }
            if (user) {
                axios
                    .get(`https://www.googleapis.com/oauth2/v1/userinfo?access_token=${user.access_token}`, {
                        headers: {
                            Authorization: `Bearer ${user.access_token}`,
                            Accept: 'application/json'
                        }
                    })
                    .then((res) => {
                        props.onSuccess(res.data);
                    })
                    .catch((err) => console.log(err));
            }
        },
        [ user ]
    );
  return (
    <Container component="main" maxWidth="xs" style={styles.container}>
      <Paper elevation={3} style={styles.paper}>
        <Typography component="h1" variant="h5" style={styles.title}>
          Conductor UI Login
        </Typography>

        <Box mt={3}>
          <Button
            onClick={login}
            fullWidth
            variant="contained"
            color="primary"
            startIcon={<GoogleIcon />}
            style={styles.googleButton}
          >
            Login with Google
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};

// Inline styles
const styles = {
  container: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
  },
  paper: {
    padding: '20px',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    backgroundColor: '#f7f7f7',
  },
  title: {
    marginBottom: '20px',
    color: '#333',
  },
  googleButton: {
    backgroundColor: '#4285F4',
    color: '#fff',
  },
};

export default LoginComponent;
