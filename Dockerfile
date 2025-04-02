FROM condaforge/miniforge3

ENV LANG=C.UTF-8 LC_ALL=C.UTF-8

SHELL ["conda", "run", "-n", "base", "/bin/bash", "-c"]

RUN \
    conda update -n base -c defaults conda && \
    conda install -c conda-forge -n base conda-pack git compilers cmake make ninja && \
    conda create -n pdal python=3.12.9 -y && \
    conda install --yes --name pdal -c conda-forge pdal --only-deps

# Ensure environment activation for future shell sessions
RUN echo "conda activate pdal" >> ~/.bashrc

# Verify Python version
RUN python --version 

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    bash \
    unzip \
    libssl-dev \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install NVM and Node.js
ENV NVM_DIR=/root/.nvm
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.4/install.sh | bash && \
    . "$NVM_DIR/nvm.sh" && \
    nvm install 22 && \
    nvm use 22 && \
    nvm alias default 22 && \
    npm install -g 3d-tiles-tools

# Ensure Node.js is in PATH
ENV PATH="$NVM_DIR/versions/node/v22.0.0/bin:$PATH"

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Install py3dtiles using Python 3.12.9
RUN curl -L -o /tmp/py3dtiles.zip https://gitlab.com/py3dtiles/py3dtiles/-/archive/main/py3dtiles-main.zip && \
    unzip /tmp/py3dtiles.zip -d /tmp && \
    cd /tmp/py3dtiles-main && \
    pip install . && \
    rm -rf /tmp/py3dtiles*

# Copy application files
COPY . .

WORKDIR /app

# ENTRYPOINT ["conda", "run", "-n", "pdal", "bash", "-c"]
ENTRYPOINT [ "bash" ]